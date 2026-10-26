package com.galeri.app;

import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.ContactPaymentRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

// Para dogrulugu (revizyon "her PR icin zorunlu"): gercek Postgres'te bastan sona bir galeri senaryosu servisler
// uzerinden oynanir; her adimdan sonra defter degismezleri kontrol edilir. Beklenen tutarlar burada sabit degil:
// TC0-TC13 altin senaryo (sabit beklenen degerlerle) ayri bir test olarak eklenecek.
@SpringBootTest
class MoneyInvariantsIT {

    private static final long TL = 100;   // kurus

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private VehicleService vehicleService;
    @Autowired private PaymentPlanService paymentPlanService;

    private UUID tenant;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Defter Testi')", tenant, "inv-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "91" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")));
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void galeriSenaryosu_herAdimdanSonraDefterDegismezleriTutar() {
        UUID partnerA = contact("Ortak A", ContactType.PERSON);
        UUID partnerB = contact("Ortak B", ContactType.PERSON);
        UUID seller = contact("Satici", ContactType.PERSON);
        UUID buyer = contact("Alici", ContactType.PERSON);
        UUID consignor = contact("Konsinye Sahibi", ContactType.PERSON);
        UUID supplier = contact("Eski Tedarikci", ContactType.ORGANIZATION);
        UUID cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        UUID bank = ledgerService.addBank(new AccountRequest("Ziraat")).id();
        check("baslangic");

        // Sermaye girisi ve virman
        capitalService.addPartner(new CapitalRequests.Partner(partnerA));
        capitalService.addPartner(new CapitalRequests.Partner(partnerB));
        capitalService.setOwner(partnerA);
        capitalService.move(partnerA, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, day(-60), 1_000_000 * TL, null));
        capitalService.move(partnerB, new CapitalRequests.Movement(CapitalRequests.Direction.IN, bank, day(-60), 500_000 * TL, null));
        check("sermaye girisi");
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, day(-59), 100_000 * TL, null));
        check("virman");

        // Acilis bakiyesi: tedarikciye borcumuz
        ledgerService.openingBalance(new CapitalRequests.Opening(null, supplier, CapitalRequests.OpeningSide.PAYABLE, day(-60), 20_000 * TL));
        check("acilis bakiyesi");

        // Pesin alis + arac masrafi (ekle, duzenle, sil)
        UUID passat = vehicleService.create(vehicle("34 INV 101", "Volkswagen", "Passat 1.5 TSI", day(-50), seller,
                600_000 * TL, cash, null, Ownership.DEALER, null)).vehicle().id();
        check("pesin alis");
        UUID paint = ledgerService.addExpense(expense(day(-45), cash, 15_000 * TL, passat, "Boya")).id();
        ledgerService.updateExpense(paint, expense(day(-45), cash, 12_500_50, passat, "Boya"));
        UUID tire = ledgerService.addExpense(expense(day(-44), bank, 7_000 * TL, passat, "Lastik")).id();
        ledgerService.deleteExpense(tire);
        check("arac masrafi ekle / duzenle / sil");

        // Taksitli satis: pesinat simdi, iki senet
        vehicleService.sell(passat, new VehicleSaleRequest(day(-30), buyer, null, 750_000 * TL, List.of(
                installment(day(-30), 250_000 * TL, PaymentMethod.CASH, true, cash),
                installment(day(0), 250_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null),
                installment(day(30), 250_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null))));
        check("taksitli satis");
        UUID note1 = installments(passat).get(1).id();
        paymentPlanService.collect(note1, new CollectRequest(bank, day(0)));
        check("senet tahsilati");
        paymentPlanService.undo(note1);
        check("tahsilat geri alma");
        paymentPlanService.collect(note1, new CollectRequest(cash, day(0)));
        check("yeniden tahsilat");

        // Planli alis: pesinat simdi, kalan senet; senet bankadan odenir
        UUID clio = vehicleService.create(vehicle("34 INV 102", "Renault", "Clio 1.0 TCe", day(-20), seller,
                400_000 * TL, null, List.of(
                        installment(day(-20), 100_000 * TL, PaymentMethod.CASH, true, cash),
                        installment(day(10), 300_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null)),
                Ownership.DEALER, null)).vehicle().id();
        check("planli alis");
        paymentPlanService.collect(installments(clio).get(1).id(), new CollectRequest(bank, day(-5)));
        check("alis senedi odemesi");

        // Satip geri alma: hic taksit odenmemis satis
        vehicleService.sell(clio, new VehicleSaleRequest(day(-3), buyer, null, 480_000 * TL, List.of(
                installment(day(20), 480_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null))));
        check("ikinci satis");
        vehicleService.undoSale(clio);
        check("satisi geri alma");

        // Konsinye: komisyon %5, sahibine masraf yansitilir, sahibine kismi odeme
        UUID egea = vehicleService.create(vehicle("34 INV 103", "Fiat", "Egea 1.3 Multijet", day(-15), null,
                null, null, null, Ownership.CONSIGNMENT, consignor)).vehicle().id();
        ledgerService.addExpense(new ExpenseRequest(day(-14), cash, 2_000 * TL, egea, null, "Yikama", null, null, true));
        ledgerService.addExpense(new ExpenseRequest(day(-14), cash, 1_000 * TL, egea, null, "Ilan", null, null, false));
        check("konsinye arac ve masraflari");
        vehicleService.sell(egea, new VehicleSaleRequest(day(-10), buyer, null, 300_000_33L, List.of(
                installment(day(-10), 300_000_33L, PaymentMethod.TRANSFER, true, bank))));
        check("konsinye satis");
        ledgerService.payContact(consignor, new ContactPaymentRequest(bank, day(-8), 200_000 * TL, null));
        check("konsinye sahibine kismi odeme");

        // Genel gider, kar payi, sermaye cekisi
        ledgerService.addExpense(expense(day(-7), cash, 8_000 * TL, null, "Kira"));
        check("genel gider");
        capitalService.allocate(new CapitalRequests.Allocation(partnerA, day(-2), 50_000 * TL, day(-60), day(0)));
        check("kar payi sermayeye");
        capitalService.move(partnerB, new CapitalRequests.Movement(CapitalRequests.Direction.OUT, cash, day(-1), 20_000 * TL, null));
        check("sermaye cekisi");

        // Hareket duzeltme (genel gider terslenir)
        UUID wrong = ledgerService.addExpense(expense(day(-1), bank, 999 * TL, null, "Kira")).id();
        ledgerService.correct(wrong);
        check("hareket duzeltme");
    }

    // ------------------------------------------------------------------ degismezler

    /** Her adimdan sonra: adim adi hata mesajinda gorunur. */
    private void check(String step) {
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

        // Rapor (donem kari, tum zamanlar) - sermayeye eklenen = defterdeki dagitilmamis kar
        CapitalResponses.PeriodProfit report = capitalService.periodProfit(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31));
        long allocated = report.partners().stream().mapToLong(CapitalResponses.PartnerProfit::allocatedCents).sum();
        assertThat(report.netCents() + report.coOwnedProfitCents() - allocated)
                .as("%s: rapor kari - sermayeye eklenen = defter", step).isEqualTo(undistributed);

        // 3) Ortak sermayeleri defterle ayni; toplam sermaye
        Map<UUID, Long> capitalByContact = new HashMap<>();
        jdbc.queryForList("select id, contact_id from account where tenant_id = ? and type = 'CAPITAL'", tenant)
                .forEach(r -> capitalByContact.put((UUID) r.get("contact_id"), -balance.get((UUID) r.get("id"))));
        List<CapitalResponses.Partner> partners = capitalService.partners();
        partners.forEach(p -> assertThat(p.capitalCents()).as("%s: %s sermayesi", step, p.name()).isEqualTo(capitalByContact.get(p.contactId())));
        assertThat(partners.stream().mapToLong(CapitalResponses.Partner::capitalCents).sum()).as("%s: toplam sermaye", step).isEqualTo(capital);

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

    // ------------------------------------------------------------------ yardimcilar

    private LocalDate day(int offset) {
        return today.plusDays(offset);
    }

    private UUID contact(String name, ContactType type) {
        return contactService.create(new ContactRequest(name, null, type, null)).id();
    }

    private List<InstallmentResponse> installments(UUID vehicleId) {
        return paymentPlanService.plans(vehicleId).stream().map(PaymentPlanResponse::installments).findFirst().orElseThrow();
    }

    private static InstallmentRequest installment(LocalDate due, long cents, PaymentMethod method, boolean paidNow, UUID account) {
        return new InstallmentRequest(due, cents, method, null, null, paidNow, account);
    }

    private static ExpenseRequest expense(LocalDate date, UUID account, long cents, UUID vehicleId, String category) {
        return new ExpenseRequest(date, account, cents, vehicleId, null, category, null, null, null);
    }

    private static VehicleRequest vehicle(String plate, String make, String model, LocalDate purchaseDate, UUID seller,
                                          Long actualCents, UUID purchaseAccount, List<InstallmentRequest> plan,
                                          Ownership ownership, UUID consignor) {
        boolean consignment = ownership == Ownership.CONSIGNMENT;
        return new VehicleRequest(plate, make, model, (short) 2020, Fuel.GASOLINE, Transmission.MANUAL, 50_000, null, null, null,
                purchaseDate, seller, null, actualCents, actualCents, purchaseAccount, plan, ownership,
                consignor, consignment ? CommissionType.RATE : null, consignment ? 500 : null, null, null, null, null);
    }
}
