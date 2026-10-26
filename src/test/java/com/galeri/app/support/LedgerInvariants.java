package com.galeri.app.support;

import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.service.CapitalService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Para dogrulugu degismezleri (revizyon "her PR icin zorunlu"): entegrasyon testleri her senaryo adimindan sonra cagirir.
// Oturum (TestSession) acik olmali; adim adi hata mesajinda gorunur.
public final class LedgerInvariants {

    private final JdbcTemplate jdbc;
    private final CapitalService capitalService;

    public LedgerInvariants(JdbcTemplate jdbc, CapitalService capitalService) {
        this.jdbc = jdbc;
        this.capitalService = capitalService;
    }

    public void check(UUID tenant, String step) {
        Map<UUID, Long> balance = new HashMap<>();
        Map<String, Long> byType = new HashMap<>();
        long total = 0, stock = 0, soldVehicles = 0;
        for (Map<String, Object> row : jdbc.queryForList("""
                select a.id, a.type, v.status,
                       coalesce((select sum(e.amount_cents) from ledger_entry e where e.target_account_id = a.id), 0)
                     - coalesce((select sum(e.amount_cents) from ledger_entry e where e.source_account_id = a.id), 0) as bal
                from account a left join vehicle v on v.id = a.vehicle_id
                where a.tenant_id = ?
                """, tenant)) {
            long bal = ((Number) row.get("bal")).longValue();
            String type = (String) row.get("type");
            balance.put((UUID) row.get("id"), bal);
            byType.merge(type, bal, Long::sum);
            total += bal;
            if ("VEHICLE".equals(type)) {
                if ("SOLD".equals(row.get("status"))) soldVehicles += bal;
                else stock += bal;
            }
        }

        // 1) Cift tarafli defter: tum hesap bakiyeleri toplami 0
        assertThat(total).as("%s: bakiyeler toplami", step).isZero();

        // 2) Toplam mal = sermaye + acilis + dagitilmamis kar
        long capital = -byType.getOrDefault("CAPITAL", 0L);
        long opening = -byType.getOrDefault("OPENING", 0L);
        long undistributed = -byType.getOrDefault("INCOME", 0L) - byType.getOrDefault("EXPENSE", 0L) - soldVehicles;
        long assets = byType.getOrDefault("CASH", 0L) + byType.getOrDefault("BANK", 0L) + stock + byType.getOrDefault("BALANCE", 0L);
        assertThat(assets).as("%s: toplam mal = sermaye + acilis + dagitilmamis kar", step).isEqualTo(capital + opening + undistributed);

        // Rapor (havuzun donem kari, tum zamanlar) - sermayeye eklenen = defterdeki dagitilmamis kar. Ortak araclarda
        // ortaklarin payi satista sermayelerine gecti: dagitilmamis kara da rapor netine de girmez.
        CapitalResponses.PeriodProfit report = capitalService.periodProfit(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31));
        long allocated = report.partners().stream().mapToLong(CapitalResponses.PartnerProfit::allocatedCents).sum();
        assertThat(report.netCents() - allocated).as("%s: rapor kari - sermayeye eklenen = defter", step).isEqualTo(undistributed);

        // 3) Ortak sermayeleri defterle ayni; her ortakta sermaye = nakitteki + araclarda bagli, nakitteki eksi degil
        Map<UUID, Long> capitalByContact = new HashMap<>();
        jdbc.queryForList("select id, contact_id from account where tenant_id = ? and type = 'CAPITAL'", tenant)
                .forEach(r -> capitalByContact.put((UUID) r.get("contact_id"), -balance.get((UUID) r.get("id"))));
        List<CapitalResponses.Partner> partners = capitalService.partners();
        for (CapitalResponses.Partner p : partners) {
            assertThat(p.capitalCents()).as("%s: %s sermayesi", step, p.name()).isEqualTo(capitalByContact.get(p.contactId()));
            assertThat(p.cashCents() + p.boundCents()).as("%s: %s sermaye = nakitteki + bagli", step, p.name()).isEqualTo(p.capitalCents());
            assertThat(p.cashCents()).as("%s: %s nakitteki sermaye", step, p.name()).isNotNegative();
            assertThat(p.boundCents()).as("%s: %s bagli sermaye", step, p.name()).isNotNegative();
        }
        assertThat(partners.stream().mapToLong(CapitalResponses.Partner::capitalCents).sum()).as("%s: toplam sermaye", step).isEqualTo(capital);
        // Bagli sermaye stoktaki araclarin maliyetini asamaz (ortaklar en fazla araclarin alis bedeli kadar katki yapar)
        assertThat(partners.stream().mapToLong(CapitalResponses.Partner::boundCents).sum())
                .as("%s: bagli sermaye <= stok", step).isLessThanOrEqualTo(stock);

        // 4) Plan toplami = taksit toplami
        assertThat(jdbc.queryForList("""
                select p.id from payment_plan p
                where p.tenant_id = ? and p.total_cents <> (select coalesce(sum(i.amount_cents), 0) from installment i where i.plan_id = p.id)
                """, UUID.class, tenant)).as("%s: plan toplami taksitlere esit olmayan planlar", step).isEmpty();

        // 5) Odenmis her taksitin hareketi gecerli (terslenmemis), dogru tipte ve ayni tutarda
        assertThat(jdbc.queryForList("""
                select i.id from installment i
                join payment_plan p on p.id = i.plan_id
                left join ledger_entry e on e.id = i.paid_entry_id
                where i.tenant_id = ? and i.paid_entry_id is not null
                  and (e.id is null or e.amount_cents <> i.amount_cents
                       or e.type <> case p.direction when 'SALE' then 'COLLECTION' else 'PAYMENT' end
                       or e.corrected_entry_id is not null
                       or exists (select 1 from ledger_entry r where r.corrected_entry_id = e.id))
                """, UUID.class, tenant)).as("%s: hareketi bozuk odenmis taksitler", step).isEmpty();

        // 6) Taksite bagli olmayan gecerli tahsilat yok
        assertThat(jdbc.queryForList("""
                select e.id from ledger_entry e
                where e.tenant_id = ? and e.type = 'COLLECTION' and e.corrected_entry_id is null
                  and not exists (select 1 from ledger_entry r where r.corrected_entry_id = e.id)
                  and not exists (select 1 from installment i where i.paid_entry_id = e.id)
                """, UUID.class, tenant)).as("%s: taksite bagli olmayan tahsilatlar", step).isEmpty();
    }
}
