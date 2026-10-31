package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.ContributionSource;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.OwnerReturnMode;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.OwnerReturnRequest;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.ShareResponse;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
import com.galeri.app.support.TestSession;
import com.galeri.app.support.TestUsers;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Ortak finansmani (revizyon PR 3) ornekleri gercek Postgres'te: her test bos bir galeride. Burak'in sermayesi 1.000.000
// (hepsi nakitte), arac 2.000.000, Havuz %50 + Burak %50. Havuzun parasi Furkan'in 10.000.000 sermayesi. Her adimdan sonra
// LedgerInvariants (bakiyeler 0, mal = sermaye + kar, ortakta sermaye = nakitteki + bagli ...).
@SpringBootTest
class CoFinancingIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock coFinancingClock() {
            return Clock.fixed(Instant.parse("2026-04-15T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private VehicleService vehicleService;
    @Autowired private PaymentPlanService paymentPlanService;
    @Autowired private com.galeri.app.service.NetWorthService netWorthService;

    private final LocalDate today = LocalDate.of(2026, 4, 15);
    private UUID tenant, owner, cash, furkan, burak, veli;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Ortak Finansman')", tenant, "cofin-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "93" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        owner = user;   // dukkan sahibi: payi eski Havuz gibi
        furkan = TestUsers.investor(jdbc, tenant, "Furkan");
        burak = TestUsers.investor(jdbc, tenant, "Burak");
        veli = person("Veli");
        capitalIn(furkan, 10_000_000 * TL);
        capitalIn(burak, 1_000_000 * TL);
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    // ------------------------------------------------------------------ revizyon ornegi (5): Burak %50

    @Test
    void katkiSermayeden_sermayedenKalsin_burak1150000_havuzPayiNeteGirer() {
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        expectPartner("alis", burak, 1_000_000 * TL, 0, 1_000_000 * TL);   // sermaye 1.000.000: nakitte 0, aracta 1.000.000
        expectCash("alis", 9_000_000 * TL);                                 // kasa -2.000.000
        // Havuz agirligi bagli sermayeyi dislar: Burak'in nakitteki sermayesi 0, havuz karindan pay almaz
        assertThat(monthProfit().partners()).filteredOn(p -> p.userId().equals(burak)).allSatisfy(p -> assertThat(p.capitalCents()).isZero());
        check("alis");

        sell(v, 2_300_000 * TL);
        expectPartner("satis", burak, 1_150_000 * TL, 1_150_000 * TL, 0);   // araçtaki parasi nakde dondu + 150.000 kar payi
        expectCash("satis", 11_300_000 * TL);
        CapitalResponses.PeriodProfit p = monthProfit();
        assertThat(p.vehicleProfitCents()).as("havuzun payi nete girer").isEqualTo(150_000 * TL);
        assertThat(p.netCents()).isEqualTo(150_000 * TL);
        assertThat(p.coOwnedProfitCents()).as("Burak'in payi sermayesine gecti").isEqualTo(150_000 * TL);
        assertThat(owner(v, burak).receivableCents()).isEqualTo(1_150_000 * TL);
        check("satis");
    }

    @Test
    void katkiCepten_sermayedenKalsin_burak2150000_kasaNetEksiBirMilyon() {
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.POCKET, null));
        expectPartner("alis", burak, 2_000_000 * TL, 1_000_000 * TL, 1_000_000 * TL);   // otomatik sermaye girisi +1.000.000
        expectCash("alis", 10_000_000 * TL);                                               // 11.000.000 + 1.000.000 - 2.000.000
        check("alis");

        sell(v, 2_300_000 * TL);
        expectPartner("satis", burak, 2_150_000 * TL, 2_150_000 * TL, 0);
        check("satis");
    }

    @Test
    void katkiSermayeden_nakitOde1150000_cekisYazilir_burakSifir() {
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        sell(v, 2_300_000 * TL, new OwnerReturnRequest(burak, OwnerReturnMode.CASH, null, cash));
        expectPartner("nakit donus", burak, 0, 0, 0);
        expectCash("nakit donus", (9_000_000 + 2_300_000 - 1_150_000) * TL);   // kasa -1.150.000
        check("nakit donus");
    }

    @Test
    void hibritDonus_500000Nakit_kalaniSermayede() {
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        sell(v, 2_300_000 * TL, new OwnerReturnRequest(burak, OwnerReturnMode.HYBRID, 500_000 * TL, cash));
        expectPartner("hibrit", burak, 650_000 * TL, 650_000 * TL, 0);
        check("hibrit");
    }

    @Test
    void ortagaDonus_alacaktanFazlaysaYaDaHesapYoksa_reddedilir_satisYazilmaz() {
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        expectError(1044, () -> sell(v, 2_300_000 * TL, new OwnerReturnRequest(burak, OwnerReturnMode.HYBRID, 1_150_001 * TL, cash)));
        expectError(1044, () -> sell(v, 2_300_000 * TL, new OwnerReturnRequest(burak, OwnerReturnMode.CASH, null, null)));
        expectPartner("reddedildi", burak, 1_000_000 * TL, 0, 1_000_000 * TL);
        check("reddedildi");
    }

    @Test
    void zarar_1900000eSatildi_burakEksi50000() {
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        sell(v, 1_900_000 * TL);
        assertThat(owner(v, burak).profitCents()).isEqualTo(-50_000 * TL);
        expectPartner("zarar", burak, 950_000 * TL, 950_000 * TL, 0);
        assertThat(monthProfit().netCents()).as("havuzun zarar payi").isEqualTo(-50_000 * TL);
        check("zarar");
    }

    @Test
    void zararKatkiyiAsarsa_ortaginAlacagiSifir_sermayedenSadeceKatkisiDuser() {
        // Arac 400.000 (katki 200.000), masraf 250.000, satis 150.000: Burak'a 75.000 - 125.000 masraf payi = 0 dagitilir
        UUID v = coOwned(400_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        ledgerService.addExpense(new ExpenseRequest(today, cash, 250_000 * TL, v, null, "Motor", null, null, null));
        sell(v, 150_000 * TL);
        assertThat(owner(v, burak).receivableCents()).isZero();
        assertThat(owner(v, burak).profitCents()).isEqualTo(-200_000 * TL);
        expectPartner("zarar", burak, 800_000 * TL, 800_000 * TL, 0);   // sermayeden sadece katkisi (200.000) duser
        check("zarar");
    }

    @Test
    void yetersizSermaye_katkiyaYetmezse_1042_kayitYok() {
        long before = jdbc.queryForObject("select count(*) from vehicle where tenant_id = ?", Long.class, tenant);
        expectError(1042, () -> coOwned(2_400_000 * TL, half(burak, ContributionSource.CAPITAL, null)));   // katki 1.200.000
        assertThat(jdbc.queryForObject("select count(*) from vehicle where tenant_id = ?", Long.class, tenant)).isEqualTo(before);
        // Hibrit: 200.000 cepten eklenince yeter
        coOwned(2_400_000 * TL, half(burak, ContributionSource.HYBRID, 200_000 * TL));
        expectPartner("hibrit katki", burak, 1_200_000 * TL, 0, 1_200_000 * TL);
        check("hibrit katki");
    }

    @Test
    void karPayiOraniSahiplikPayindanFarkli_herTahsilatKarPayiOranlaBolunur() {
        // Sahiplik %50 / %50, kar payi %40 / %60: satis 2.300.000'in %60'i Burak'a (katkisi 1.000.000)
        UUID v = coOwned(2_000_000 * TL, List.of(new ShareRequest(owner, 5000, 4000, null, null),
                new ShareRequest(burak, 5000, 6000, null, null)));
        sell(v, 2_300_000 * TL);
        assertThat(owner(v, burak).receivableCents()).isEqualTo(1_380_000 * TL);
        assertThat(owner(v, burak).profitCents()).isEqualTo(380_000 * TL);
        assertThat(monthProfit().netCents()).isEqualTo(-80_000 * TL);   // dukkan sahibi: 920.000 - 1.000.000
        expectPartner("oran", burak, 1_380_000 * TL, 1_380_000 * TL, 0);
        check("oran");
    }

    @Test
    void taksitliSatis_herTahsilattaKarPayiOraniylaDagitilir_kalaniAlacak_karsiliksizdaGeriAlinir() {
        // %50 / %50, alis 2.000.000; satis 2.300.000 = pesin 1.000.000 + senet 1.300.000
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        UUID buyer = person("Alici");
        vehicleService.sell(v, sale(buyer, List.of(
                new InstallmentRequest(today, 1_000_000 * TL, PaymentMethod.CASH, null, null, true, cash),
                new InstallmentRequest(today.plusDays(30), 1_300_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null))));
        ShareResponse b = owner(v, burak);
        assertThat(b.receivableCents()).isEqualTo(1_150_000 * TL);
        assertThat(b.collectedCents()).as("pesinin yarisi").isEqualTo(500_000 * TL);
        expectPartner("pesin", burak, 500_000 * TL, 500_000 * TL, 0);   // katki satisa gecti, pesinin yarisi sermayede
        assertThat(holder(burak).otherCents()).as("tahsil edilmemis payi alacak").isEqualTo(650_000 * TL);
        assertThat(holder(burak).capitalCents()).isEqualTo(1_150_000 * TL);
        check("pesin");

        UUID note = paymentPlanService.plans(v).stream().filter(p -> p.direction() == PlanDirection.SALE).findFirst().orElseThrow()
                .installments().stream().filter(i -> i.status() != InstallmentStatus.PAID).findFirst().orElseThrow().id();
        paymentPlanService.collect(note, new CollectRequest(cash, today.plusDays(30)));
        expectPartner("senet tahsil", burak, 1_150_000 * TL, 1_150_000 * TL, 0);
        assertThat(holder(burak).otherCents()).isZero();
        check("senet tahsil");

        paymentPlanService.undo(note);
        expectPartner("tahsilat geri", burak, 500_000 * TL, 500_000 * TL, 0);
        assertThat(holder(burak).otherCents()).isEqualTo(650_000 * TL);
        check("tahsilat geri");
    }

    @Test
    void dukkanPayi_oran10_ortaginPayindanDuser_havuzaKalir() {
        UUID v = vehicleService.create(request(2_000_000 * TL, cash, null, half(burak, ContributionSource.CAPITAL, null),
                CommissionType.RATE, 1000, null)).vehicle().id();
        sell(v, 2_300_000 * TL);
        assertThat(owner(v, burak).profitCents()).isEqualTo(135_000 * TL);   // 150.000 - %10
        assertThat(monthProfit().netCents()).isEqualTo(165_000 * TL);         // havuz 150.000 + dukkan payi 15.000
        check("dukkan payi");
    }

    @Test
    void ucOrtak_kurusYuvarlama_paylarToplamiKaraEsit() {
        UUID selin = TestUsers.investor(jdbc, tenant, "Selin"), kerem = TestUsers.investor(jdbc, tenant, "Kerem");
        capitalIn(selin, 1_000_000 * TL);
        capitalIn(kerem, 1_000_000 * TL);
        UUID v = coOwned(1_500_000 * TL, List.of(new ShareRequest(furkan, 3334), new ShareRequest(selin, 3333), new ShareRequest(kerem, 3333)));
        sell(v, 1_500_000 * TL + 100_000_01L);   // kar 100.000,01
        assertThat(cost(v).shares()).extracting(ShareResponse::profitCents).containsExactly(33_340_01L, 33_330_00L, 33_330_00L);
        assertThat(cost(v).shares()).extracting(ShareResponse::contributionCents)
                .containsExactly(500_100_00L, 499_950_00L, 499_950_00L);   // katki da ayni kuralla, toplam 1.500.000
        assertThat(monthProfit().netCents()).as("havuz yok: nete giren 0").isZero();
        check("uc ortak");
    }

    @Test
    void satisiGeriAl_karPaylariTerslenir_nakitDonusVarsaOnce1043() {
        UUID v = coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        sell(v, 2_300_000 * TL, new OwnerReturnRequest(burak, OwnerReturnMode.HYBRID, 100_000 * TL, cash));
        expectError(1023, () -> ledgerService.correct(jdbc.queryForObject(
                "select id from ledger_entry where vehicle_id = ? and type = 'VEHICLE_PROFIT_SHARE'", UUID.class, v)));   // kar payi bagli
        undoCollections(v);
        expectError(1043, () -> vehicleService.undoSale(v));

        UUID returnEntry = jdbc.queryForObject("""
                select e.id from ledger_entry e join account t on t.id = e.target_account_id
                where e.vehicle_id = ? and e.type = 'CAPITAL' and t.type = 'CAPITAL'""", UUID.class, v);
        ledgerService.correct(returnEntry);   // Kasa'dan ortaga donus cekisini duzelt
        vehicleService.undoSale(v);
        expectPartner("geri alindi", burak, 1_000_000 * TL, 0, 1_000_000 * TL);   // kar payi terslendi, katki yine aracta
        assertThat(monthProfit().coOwnedProfitCents()).isZero();
        check("geri alindi");
    }

    @Test
    void ortakTaksitliAlis_herOdemePayaGoreBolunur_satistaAlacakOdenenKatkiArtiKarPayi() {
        // Arac 2.000.000, %50/%50, pesinat 800.000 + senet 1.200.000; satis 2.300.000 senetler odenmeden
        UUID seller = person("Satici");
        UUID v = vehicleService.create(request(2_000_000 * TL, null, seller, half(burak, ContributionSource.CAPITAL, null), null, null, null,
                List.of(new InstallmentRequest(today, 800_000 * TL, PaymentMethod.CASH, null, null, true, cash),
                        new InstallmentRequest(today.plusDays(30), 1_200_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null))))
                .vehicle().id();
        expectPartner("pesinat", burak, 1_000_000 * TL, 600_000 * TL, 400_000 * TL);
        check("pesinat");

        sell(v, 2_300_000 * TL);
        ShareResponse b = owner(v, burak);
        assertThat(b.contributionCents()).isEqualTo(400_000 * TL);
        assertThat(b.profitCents()).as("kar tam maliyet uzerinden").isEqualTo(150_000 * TL);
        assertThat(b.receivableCents()).isEqualTo(550_000 * TL);
        assertThat(owner(v, owner).receivableCents()).isEqualTo(550_000 * TL);   // katki 400.000 + kar 150.000 (kalan senet satis parasindan)
        expectPartner("satis", burak, 1_150_000 * TL, 1_150_000 * TL, 0);
        check("satis");

        // Kalan senedi satis parasindan havuz oder: Burak'in sermayesi degismez
        UUID note = paymentPlanService.plans(v).stream().filter(p -> p.direction() == PlanDirection.PURCHASE).findFirst().orElseThrow()
                .installments().stream().filter(i -> i.status() != InstallmentStatus.PAID).findFirst().orElseThrow().id();
        paymentPlanService.collect(note, new CollectRequest(cash, today.plusDays(30)));
        expectPartner("senet odendi", burak, 1_150_000 * TL, 1_150_000 * TL, 0);
        check("senet odendi");
    }

    @Test
    void ortakTaksitliAlis_cepten_herOdemedeOtomatikSermayeGirisi() {
        UUID seller = person("Satici");
        UUID v = vehicleService.create(request(2_000_000 * TL, null, seller, half(burak, ContributionSource.POCKET, null), null, null, null,
                List.of(new InstallmentRequest(today, 800_000 * TL, PaymentMethod.CASH, null, null, true, cash),
                        new InstallmentRequest(today.plusDays(30), 1_200_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null))))
                .vehicle().id();
        expectPartner("pesinat", burak, 1_400_000 * TL, 1_000_000 * TL, 400_000 * TL);
        UUID note = paymentPlanService.plans(v).getFirst().installments().stream()
                .filter(i -> i.status() != InstallmentStatus.PAID).findFirst().orElseThrow().id();
        paymentPlanService.collect(note, new CollectRequest(cash, today));
        expectPartner("senet", burak, 2_000_000 * TL, 1_000_000 * TL, 1_000_000 * TL);
        expectCash("senet", (11_000_000 - 800_000 + 400_000 - 1_200_000 + 600_000) * TL);
        check("senet");

        paymentPlanService.undo(note);   // senet odemesi geri: cepten giris de terslenir
        expectPartner("senet geri", burak, 1_400_000 * TL, 1_000_000 * TL, 400_000 * TL);
        check("senet geri");
    }

    // ------------------------------------------------------------------ yardimcilar

    @Test
    void varliklar_baskaGalerininSermayeVeAraclariniGostermez() {
        coOwned(2_000_000 * TL, half(burak, ContributionSource.CAPITAL, null));
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        var worth = netWorthService.summary();
        assertThat(worth.totalCents()).isZero();
        assertThat(worth.capitalCents()).isZero();
        assertThat(worth.partners()).isEmpty();
        assertThat(worth.vehicles()).isEmpty();
    }

    @Test
    void varliklar_kurusluTaksitlerTamOdendigindeSahipBorclariSifirlanir() {
        UUID seller = person("Satici");
        vehicleService.create(request(101, null, seller, half(burak, ContributionSource.CAPITAL, null), null, null, null,
                List.of(new InstallmentRequest(today, 1L, PaymentMethod.CASH, null, null, true, cash),
                        new InstallmentRequest(today, 1L, PaymentMethod.CASH, null, null, true, cash),
                        new InstallmentRequest(today, 99L, PaymentMethod.CASH, null, null, true, cash))));
        var stock = netWorthService.summary().vehicles().getFirst();
        assertThat(stock.costCents()).isEqualTo(101);
        assertThat(stock.owners().stream().mapToLong(o -> o.assetCents()).sum()).isEqualTo(101);
        assertThat(stock.owners()).allSatisfy(o -> assertThat(o.debtCents()).isZero());
        check("kuruslu taksitler");
    }

    @Test
    void varliklar_senetliOrtakAractaBrutMalBorcVeNetAyriGosterilir() {
        UUID seller = person("Satici");
        UUID v = vehicleService.create(request(2_000_000 * TL, null, seller,
                half(burak, ContributionSource.CAPITAL, null), null, null, null,
                List.of(new InstallmentRequest(today, 800_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null),
                        new InstallmentRequest(today.plusDays(30), 1_200_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null))))
                .vehicle().id();
        var before = netWorthService.summary();
        assertThat(before.stockCents()).isEqualTo(2_000_000 * TL);
        assertThat(before.payableCents()).isEqualTo(2_000_000 * TL);
        assertThat(before.vehicles().getFirst().owners()).allSatisfy(o -> {
            assertThat(o.assetCents()).isEqualTo(1_000_000 * TL);
            assertThat(o.debtCents()).isEqualTo(1_000_000 * TL);
            assertThat(o.netCents()).isZero();
        });
        var note = paymentPlanService.plans(v).getFirst().installments().getFirst();
        paymentPlanService.collect(note.id(), new CollectRequest(cash, today));
        var after = netWorthService.summary();
        assertThat(after.stockCents()).isEqualTo(2_000_000 * TL);
        assertThat(after.payableCents()).isEqualTo(1_200_000 * TL);
        assertThat(after.vehicles().getFirst().owners()).allSatisfy(o -> {
            assertThat(o.assetCents()).isEqualTo(1_000_000 * TL);
            assertThat(o.debtCents()).isEqualTo(600_000 * TL);
            assertThat(o.netCents()).isEqualTo(400_000 * TL);
            assertThat(o.contributionCents()).isEqualTo(400_000 * TL);
        });
        check("varliklar senet odemesi");
    }

    private UUID person(String name) {
        return contactService.create(new ContactRequest(name, null, ContactType.PERSON, null)).id();
    }

    private void capitalIn(UUID partner, long cents) {
        capitalService.move(partner, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, cents, null));
    }

    private List<ShareRequest> half(UUID partner, ContributionSource source, Long pocketCents) {
        return List.of(new ShareRequest(owner, 5000), new ShareRequest(partner, 5000, null, source, pocketCents));
    }

    private UUID coOwned(long price, List<ShareRequest> owners) {
        return vehicleService.create(request(price, cash, null, owners, null, null, null)).vehicle().id();
    }

    private VehicleRequest request(long price, UUID account, UUID seller, List<ShareRequest> owners,
                                   CommissionType cutType, Integer cutBp, Long cutCents) {
        return request(price, account, seller, owners, cutType, cutBp, cutCents, null);
    }

    private VehicleRequest request(long price, UUID account, UUID seller, List<ShareRequest> owners,
                                   CommissionType cutType, Integer cutBp, Long cutCents, List<InstallmentRequest> plan) {
        return new VehicleRequest("34 OF " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, seller, null, price, price, account, plan, Ownership.CO_OWNED, null, null, null, null,
                owners, null, null, cutType, cutBp, cutCents);
    }

    private void sell(UUID vehicle, long price, OwnerReturnRequest... returns) {
        vehicleService.sell(vehicle, new VehicleSaleRequest(today, veli, null, price,
                List.of(new InstallmentRequest(today, price, PaymentMethod.CASH, null, null, true, cash)), List.of(returns)));
    }

    private VehicleSaleRequest sale(UUID buyer, List<InstallmentRequest> rows) {
        return new VehicleSaleRequest(today, buyer, null, rows.stream().mapToLong(InstallmentRequest::amountCents).sum(), rows);
    }

    private CapitalResponses.Holder holder(UUID user) {
        return netWorthService.holders().stream().filter(h -> user.equals(h.userId())).findFirst().orElseThrow();
    }

    /** Satisi geri almadan once alicinin pesin tahsilatini geri al (odenmis taksit varsa 1018). */
    private void undoCollections(UUID vehicle) {
        paymentPlanService.plans(vehicle).stream().filter(p -> p.direction() == PlanDirection.SALE)
                .flatMap(p -> p.installments().stream()).filter(i -> i.status() == InstallmentStatus.PAID)
                .forEach(i -> paymentPlanService.undo(i.id()));
    }

    private VehicleCost cost(UUID vehicle) {
        return ledgerService.vehicleCost(vehicle);
    }

    private ShareResponse owner(UUID vehicle, UUID contact) {
        return cost(vehicle).shares().stream().filter(s -> java.util.Objects.equals(s.userId(), contact)).findFirst().orElseThrow();
    }

    private CapitalResponses.PeriodProfit monthProfit() {
        return capitalService.periodProfit(today.withDayOfMonth(1), today.withDayOfMonth(30));
    }

    private void expectPartner(String step, UUID contact, long capital, long cashPart, long bound) {
        CapitalResponses.Partner p = capitalService.partners().stream().filter(x -> x.userId().equals(contact)).findFirst().orElseThrow();
        assertThat(p.capitalCents()).as("%s: sermaye", step).isEqualTo(capital);
        assertThat(p.cashCents()).as("%s: nakitteki", step).isEqualTo(cashPart);
        assertThat(p.boundCents()).as("%s: aracta bagli", step).isEqualTo(bound);
    }

    private void expectCash(String step, long cents) {
        assertThat(ledgerService.moneyAccounts().stream().filter(a -> a.id().equals(cash)).findFirst().orElseThrow().balanceCents())
                .as("%s: Kasa", step).isEqualTo(cents);
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
        var worth = netWorthService.summary();
        assertThat(worth.differenceCents()).as("%s: varlik ekrani dengesi", step).isZero();
        assertThat(worth.partners()).allSatisfy(p -> {
            long bound = worth.vehicles().stream().flatMap(v -> v.owners().stream())
                    .filter(o -> !o.owner() && p.userId().equals(o.userId())).mapToLong(o -> o.contributionCents()).sum();
            assertThat(p.boundCents()).as("%s: araclara gore bagli sermaye", step).isEqualTo(bound);
        });
        assertThat(worth.vehicles()).allSatisfy(v -> {
            assertThat(v.owners().stream().mapToLong(o -> o.assetCents()).sum()).isEqualTo(v.costCents());
            assertThat(v.owners().stream().mapToLong(o -> o.debtCents()).sum()).isEqualTo(v.debtCents());
        });
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
