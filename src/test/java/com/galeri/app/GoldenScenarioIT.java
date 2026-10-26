package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.ContactPaymentRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.DashboardResponse;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.model.response.ShareResponse;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.DashboardService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
import com.galeri.app.support.TestSession;
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

// Altin senaryo: docs/SENARYOLAR.md TC0-TC13, bos galeride sirayla, sabit Clock (hepsi ayni gun). Beklenen degerler
// birikimli ve kurusu kurusuna; her adimdan sonra LedgerInvariants da kosar. Beklenen deger degisirse PR'da nedeni yazilir.
@SpringBootTest
class GoldenScenarioIT {

    /** 15.03.2026 10:00 Istanbul: senaryonun "bugun"u; donem kari = Mart 2026. */
    static final Instant NOW = Instant.parse("2026-03-15T07:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock goldenClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private VehicleService vehicleService;
    @Autowired private PaymentPlanService paymentPlanService;
    @Autowired private DashboardService dashboardService;

    private final LocalDate today = LocalDate.of(2026, 3, 15);
    private UUID tenant;
    private UUID cash, bank;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Altin Senaryo')", tenant, "golden-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "92" + suffix);
        TestSession.open(user, tenant, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void altinSenaryo_TC0danTC13e_beklenenDegerlerKurusuKurusunaTutar() {
        // ---------------------------------------------------------------- TC0 hazirlik
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        bank = ledgerService.addBank(new AccountRequest("Ziraat")).id();
        UUID furkan = person("Furkan"), selin = person("Selin"), burak = person("Burak Şahin");
        UUID ali = person("Ali"), hasan = person("Hasan"), veli = person("Veli"), can = person("Can");
        UUID mehmet = person("Mehmet"), ayse = person("Ayşe");
        UUID eminevim = contactService.create(new ContactRequest("Eminevim", null, ContactType.ORGANIZATION, null)).id();
        for (UUID p : List.of(furkan, selin, burak)) capitalService.addPartner(new CapitalRequests.Partner(p));
        capitalService.setOwner(furkan);
        capitalIn(furkan, cash, tl(40_000_000));
        capitalIn(selin, bank, tl(8_000_000));
        capitalIn(burak, cash, tl(2_000_000));
        expectMoney("TC0", tl(42_000_000), tl(8_000_000));
        assertThat(cashBalance() + bankBalance()).as("TC0 toplam nakit").isEqualTo(tl(50_000_000));
        expectShare("TC0", furkan, 8000);
        expectShare("TC0", selin, 1600);
        expectShare("TC0", burak, 400);
        check("TC0");

        // ---------------------------------------------------------------- TC1 nakit alim, nakit satim
        UUID v1 = dealer("34 TC 001", null, tl(1_000_000), cash, null);
        expense(v1, tl(20_000), "Boya", null);
        assertThat(cost(v1).totalCents()).as("TC1 maliyet").isEqualTo(tl(1_020_000));
        sell(v1, veli, tl(1_150_000), paidNow(tl(1_150_000), cash));
        assertThat(cost(v1).profitCents()).as("TC1 kar").isEqualTo(tl(130_000));
        expectCari("TC1", veli, 0);
        expectMoney("TC1", tl(42_130_000), tl(8_000_000));
        check("TC1");

        // ---------------------------------------------------------------- TC2 nakit alim, parcali satim
        UUID v2 = dealer("34 TC 002", null, tl(800_000), cash, null);
        sell(v2, can, tl(950_000), paidNow(tl(350_000), cash),
                note(today.plusDays(30), tl(200_000)), note(today.plusDays(60), tl(200_000)), note(today.plusDays(90), tl(200_000)));
        assertThat(saleNotes(v2)).as("TC2 senetler").containsExactly(tl(200_000), tl(200_000), tl(200_000));
        assertThat(cost(v2).profitCents()).as("TC2 kar").isEqualTo(tl(150_000));
        expectCari("TC2", can, tl(600_000));
        expectMoney("TC2", tl(41_680_000), tl(8_000_000));
        collect(firstUnpaid(v2, PlanDirection.SALE), bank);
        expectMoney("TC2 senet tahsili", tl(41_680_000), tl(8_200_000));
        expectCari("TC2 senet tahsili", can, tl(400_000));
        check("TC2");

        // ---------------------------------------------------------------- TC3 parcali alim, nakit satim
        UUID v3 = dealer("34 TC 003", ali, tl(1_000_000), null, List.of(
                paidNow(tl(400_000), cash), note(today.plusDays(15), tl(300_000)), note(today.plusDays(45), tl(300_000))));
        expectMoney("TC3", tl(41_280_000), tl(8_200_000));
        expectCari("TC3", ali, -tl(600_000));
        assertThat(dashboard().payableCents()).as("TC3 ana ekran verecek").isEqualTo(tl(600_000));
        assertThat(openInstallments(PlanDirection.PURCHASE)).as("TC3 odenecek senetler").hasSize(2);
        sell(v3, veli, tl(1_120_000), paidNow(tl(1_120_000), cash));
        assertThat(cost(v3).profitCents()).as("TC3 kar").isEqualTo(tl(120_000));
        expectMoney("TC3 satis", tl(42_400_000), tl(8_200_000));
        collect(firstUnpaid(v3, PlanDirection.PURCHASE), cash);
        expectMoney("TC3 senet odendi", tl(42_100_000), tl(8_200_000));
        expectCari("TC3 senet odendi", ali, -tl(300_000));
        check("TC3");

        // ---------------------------------------------------------------- TC4 parcali alim, parcali satim, gecikme
        UUID v4 = dealer("34 TC 004", hasan, tl(600_000), null, List.of(paidNow(tl(200_000), cash), note(today.plusDays(30), tl(400_000))));
        expectMoney("TC4 alis", tl(41_900_000), tl(8_200_000));
        expectCari("TC4 alis", hasan, -tl(400_000));
        sell(v4, can, tl(750_000), paidNow(tl(250_000), cash), note(today.minusDays(1), tl(250_000)), note(today.plusMonths(1), tl(250_000)));
        assertThat(cost(v4).profitCents()).as("TC4 kar").isEqualTo(tl(150_000));
        expectMoney("TC4 satis", tl(42_150_000), tl(8_200_000));
        expectCari("TC4 satis", can, tl(900_000));
        List<InstallmentResponse> overdue = paymentPlanService.list(InstallmentStatus.OVERDUE, PlanDirection.SALE, null, null);
        assertThat(overdue).as("TC4 geciken").hasSize(1);
        assertThat(overdue.getFirst().payer().id()).isEqualTo(can);
        assertThat(overdue.getFirst().amountCents()).isEqualTo(tl(250_000));
        assertThat(overdue.getFirst().daysLate()).as("TC4 1 gun gecikti").isEqualTo(1);
        DashboardResponse d4 = dashboard();
        assertThat(d4.overdueCount()).as("TC4 ana ekran geciken adet").isEqualTo(1);
        assertThat(d4.overdueCents()).as("TC4 ana ekran geciken tutar").isEqualTo(tl(250_000));
        collect(firstUnpaid(v4, PlanDirection.PURCHASE), cash);
        expectMoney("TC4 senet odendi", tl(41_750_000), tl(8_200_000));
        expectCari("TC4 senet odendi", hasan, 0);
        check("TC4");

        // ---------------------------------------------------------------- TC5 nakit alim, Evim satis
        UUID v5 = dealer("34 TC 005", null, tl(1_500_000), cash, null);
        sell(v5, veli, tl(1_700_000), paidNow(tl(340_000), cash),
                new InstallmentRequest(today.plusDays(10), tl(1_360_000), PaymentMethod.INSTITUTION, null, eminevim, false, null));
        expectMoney("TC5", tl(40_590_000), tl(8_200_000));
        expectCari("TC5", veli, 0);
        expectCari("TC5", eminevim, tl(1_360_000));
        assertThat(cost(v5).profitCents()).as("TC5 kar").isEqualTo(tl(200_000));
        collect(firstUnpaid(v5, PlanDirection.SALE), bank);
        expectMoney("TC5 kurum tahsili", tl(40_590_000), tl(9_560_000));
        expectCari("TC5 kurum tahsili", eminevim, 0);
        check("TC5");

        // ---------------------------------------------------------------- TC6 konsinye, nakit satis
        UUID v6 = consignment("34 TC 006", mehmet, CommissionType.RATE, 300, null);
        expense(v6, tl(10_000), "Yıkama", false);
        expense(v6, tl(5_000), "Bakım", true);
        expectMoney("TC6", tl(40_575_000), tl(9_560_000));
        expectCari("TC6", mehmet, tl(5_000));
        assertThat(dashboard().consignmentCount()).as("TC6 +1 konsinye arac").isEqualTo(1);
        sell(v6, veli, tl(2_000_000), paidNow(tl(2_000_000), cash));
        VehicleCost c6 = cost(v6);
        assertThat(c6.commissionCents()).as("TC6 komisyon").isEqualTo(tl(60_000));
        assertThat(c6.ownerPayoutCents()).as("TC6 sahibine borc").isEqualTo(tl(1_940_000));
        assertThat(c6.profitCents()).as("TC6 arac kari").isEqualTo(tl(50_000));
        expectCari("TC6 satis", mehmet, -tl(1_935_000));
        expectMoney("TC6 satis", tl(42_575_000), tl(9_560_000));
        pay(mehmet, cash, tl(1_935_000));
        expectCari("TC6 odendi", mehmet, 0);
        expectMoney("TC6 odendi", tl(40_640_000), tl(9_560_000));
        check("TC6");

        // ---------------------------------------------------------------- TC7 konsinye, parcali satis
        UUID v7 = consignment("34 TC 007", ayse, CommissionType.FIXED, null, tl(40_000));
        sell(v7, can, tl(1_000_000), paidNow(tl(300_000), cash), note(today.plusDays(30), tl(350_000)), note(today.plusDays(60), tl(350_000)));
        expectMoney("TC7", tl(40_940_000), tl(9_560_000));
        expectCari("TC7", ayse, -tl(960_000));
        expectCari("TC7", can, tl(1_600_000));
        pay(ayse, cash, tl(260_000));
        expectCari("TC7 kismi odeme", ayse, -tl(700_000));
        expectMoney("TC7 kismi odeme", tl(40_680_000), tl(9_560_000));
        check("TC7");

        // ---------------------------------------------------------------- TC8 ortak arac (ortak finansmani, revizyon PR 3)
        // Furkan %60 + Selin %40, Havuz yok; katki sermayeden (500.000 kasadan cikar, 300.000 / 200.000 araca baglanir).
        // Eski model: kar 100.000 havuz disi gelirde kaliyor, sermayeler degismiyordu. Yeni: kar payi satista sermayeye.
        UUID v8 = create(request("34 TC 008", null, tl(500_000), cash, null, Ownership.CO_OWNED, null, null, null, null,
                List.of(new ShareRequest(furkan, 6000), new ShareRequest(selin, 4000))));
        expectBound("TC8 stokta", furkan, tl(300_000));
        expectBound("TC8 stokta", selin, tl(200_000));
        expectCapital("TC8 stokta", furkan, tl(40_000_000));   // sermaye ayni, bir kismi aracta
        sell(v8, veli, tl(600_000), paidNow(tl(600_000), cash));
        VehicleCost c8 = cost(v8);
        assertThat(c8.profitCents()).as("TC8 kar").isEqualTo(tl(100_000));
        assertThat(c8.shares()).extracting(ShareResponse::profitCents).as("TC8 kar paylari").containsExactly(tl(60_000), tl(40_000));
        assertThat(c8.shares()).extracting(ShareResponse::receivableCents).as("TC8 alacak = katki + kar payi")
                .containsExactly(tl(360_000), tl(240_000));
        expectMoney("TC8", tl(40_780_000), tl(9_560_000));    // degismedi: para kasadan cikip kasaya girdi
        expectCapital("TC8", furkan, tl(40_060_000));         // eski 40.000.000
        expectCapital("TC8", selin, tl(8_040_000));           // eski 8.000.000
        expectBound("TC8 satildi", furkan, 0);
        check("TC8");

        // ---------------------------------------------------------------- TC9 Burak Sahin (konsinye yolu)
        expectError(1036, () -> capitalService.move(burak, new CapitalRequests.Movement(CapitalRequests.Direction.OUT, cash, today, 2_000_001 * 100L, null)));
        capitalService.move(burak, new CapitalRequests.Movement(CapitalRequests.Direction.OUT, cash, today, tl(1_200_000), null));
        expectCapital("TC9 cekis", burak, tl(800_000));
        expectShare("TC9 cekis", burak, 164);
        expectMoney("TC9 cekis", tl(39_580_000), tl(9_560_000));
        UUID v9 = consignment("34 TC 009", burak, CommissionType.FIXED, null, 0L);
        expense(v9, tl(30_000), "Bakım", true);
        sell(v9, veli, tl(1_400_000), paidNow(tl(1_400_000), cash));
        expectCari("TC9 satis", burak, -tl(1_370_000));
        expectMoney("TC9 satis", tl(40_950_000), tl(9_560_000));
        pay(burak, cash, tl(1_370_000));
        capitalIn(burak, cash, tl(1_370_000));
        expectCari("TC9 sermayeye", burak, 0);
        expectCapital("TC9 sermayeye", burak, tl(2_170_000));
        expectMoney("TC9 sermayeye", tl(40_950_000), tl(9_560_000));
        check("TC9");

        // ---------------------------------------------------------------- TC10 ara kontrol
        expectMoney("TC10", tl(40_950_000), tl(9_560_000));
        assertThat(cashBalance() + bankBalance()).as("TC10 toplam nakit").isEqualTo(tl(50_510_000));
        DashboardResponse d10 = dashboard();
        assertThat(d10.receivableCents()).as("TC10 alacak").isEqualTo(tl(1_600_000));
        assertThat(d10.payableCents()).as("TC10 verecek").isEqualTo(tl(1_000_000));
        expectCari("TC10", can, tl(1_600_000));
        expectCari("TC10", ayse, -tl(700_000));
        expectCari("TC10", ali, -tl(300_000));
        for (UUID zero : List.of(veli, hasan, mehmet, burak, eminevim)) expectCari("TC10", zero, 0);
        // TC8 yeni modelle: ortaklarin kar payi (100.000) sermayelerine gecti -> sermaye, pay ve oneriler degisti; net ayni.
        expectCapital("TC10", furkan, tl(40_060_000));   // eski 40.000.000
        expectCapital("TC10", selin, tl(8_040_000));     // eski 8.000.000
        expectCapital("TC10", burak, tl(2_170_000));
        expectShare("TC10", furkan, 7969);               // eski 7973
        expectShare("TC10", selin, 1599);                // eski 1595
        expectShare("TC10", burak, 432);                 // eski 433
        CapitalResponses.PeriodProfit p10 = monthProfit();
        assertThat(p10.vehicleProfitCents()).as("TC10 arac kari").isEqualTo(tl(750_000));   // TC8'de havuz payi yok
        assertThat(p10.commissionCents()).as("TC10 komisyon").isEqualTo(tl(100_000));
        assertThat(p10.generalExpenseCents()).as("TC10 genel gider").isEqualTo(tl(10_000));
        assertThat(p10.netCents()).as("TC10 net").isEqualTo(tl(840_000));
        assertThat(p10.coOwnedProfitCents()).as("TC10 ortaklara yazilan kar payi").isEqualTo(tl(100_000));
        expectSuggested("TC10", p10, furkan, 66_939_329);   // eski 66.972.296
        expectSuggested("TC10", p10, selin, 13_434_652);    // eski 13.394.458
        expectSuggested("TC10", p10, burak, 3_626_019);     // eski 3.633.246
        assertThat(p10.partners().stream().mapToLong(CapitalResponses.PartnerProfit::suggestedCents).sum()).isEqualTo(tl(840_000));
        long capital10 = capitalService.partners().stream().mapToLong(CapitalResponses.Partner::capitalCents).sum();
        assertThat(cashBalance() + bankBalance() + d10.receivableCents() - d10.payableCents()).as("TC10 denge")
                .isEqualTo(tl(51_110_000))
                .isEqualTo(capital10 + p10.netCents());   // eski: + ortakli 100.000 (dagitilmamis kardaydi)
        assertThat(capital10).isEqualTo(tl(50_270_000));   // eski 50.170.000
        check("TC10");

        // ---------------------------------------------------------------- TC11 kar payini sermayeye ekle ve cek
        allocate(furkan, 66_939_329);
        expectCapital("TC11", furkan, 4_072_939_329L);
        expectMoney("TC11", tl(40_950_000), tl(9_560_000));
        CapitalResponses.PeriodProfit p11 = monthProfit();
        expectSuggested("TC11 oneriler ayni", p11, furkan, 66_939_329);
        expectSuggested("TC11 oneriler ayni", p11, selin, 13_434_652);
        expectSuggested("TC11 oneriler ayni", p11, burak, 3_626_019);
        assertThat(partnerProfit(p11, furkan).allocatedCents()).as("TC11 Furkan eklenen").isEqualTo(66_939_329);
        allocate(selin, 13_434_652);
        allocate(burak, 3_626_019);
        expectShare("TC11", furkan, 7969);
        expectShare("TC11", selin, 1599);
        expectShare("TC11", burak, 432);
        capitalService.move(selin, new CapitalRequests.Movement(CapitalRequests.Direction.OUT, bank, today, 13_434_652L, null));
        expectMoney("TC11 Selin cekis", tl(40_950_000), 942_565_348);   // eski 942.605.542
        expectCapital("TC11 Selin cekis", selin, tl(8_040_000));          // eski 8.000.000
        check("TC11");

        // ---------------------------------------------------------------- TC12 geri alma zinciri
        InstallmentResponse late = paymentPlanService.list(InstallmentStatus.OVERDUE, PlanDirection.SALE, null, null).getFirst();
        collect(late.id(), cash);
        expectMoney("TC12 tahsil", tl(41_200_000), 942_565_348);
        paymentPlanService.undo(late.id());
        expectMoney("TC12 geri al", tl(40_950_000), 942_565_348);
        assertThat(paymentPlanService.list(InstallmentStatus.OVERDUE, PlanDirection.SALE, null, null))
                .extracting(InstallmentResponse::id).as("TC12 senet yine gecikmis").containsExactly(late.id());
        assertThat(ledgerService.entries(cash)).as("TC12 duzeltme satiri")
                .anyMatch(e -> "Düzeltme: Senet tahsilatı".equals(e.description()));
        expectError(1018, () -> vehicleService.undoSale(v1));
        paymentPlanService.undo(paymentPlanService.plans(v1).stream()
                .filter(p -> p.direction() == PlanDirection.SALE).findFirst().orElseThrow().installments().getFirst().id());
        expectMoney("TC12 pesin geri", tl(39_800_000), 942_565_348);
        vehicleService.undoSale(v1);
        assertThat(vehicleStatus(v1)).as("TC12 arac stokta").isEqualTo(VehicleStatus.IN_STOCK);
        expectCari("TC12", veli, 0);
        assertThat(monthProfit().vehicleProfitCents()).as("TC12 donem arac kari").isEqualTo(tl(620_000));
        expectError(1023, () -> ledgerService.correct(activeEntry(v2, "VEHICLE_SALE")));
        expectError(1023, () -> ledgerService.correct(activeEntry(v6, "CONSIGNMENT_SETTLEMENT")));
        check("TC12");

        // ---------------------------------------------------------------- TC13 virman
        long net13 = monthProfit().netCents();
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, tl(1_000_000), null));
        expectMoney("TC13", tl(38_800_000), 942_565_348 + tl(1_000_000));   // TC12 sonu Kasa 39.800.000
        assertThat(monthProfit().netCents()).as("TC13 kar degismez").isEqualTo(net13);
        expectCari("TC13", can, tl(1_600_000));
        expectError(1037, () -> ledgerService.transfer(new CapitalRequests.Transfer(cash, cash, today, tl(1), null)));
        check("TC13");
    }

    // ------------------------------------------------------------------ islemler

    private static long tl(long lira) {
        return lira * 100;
    }

    private UUID person(String name) {
        return contactService.create(new ContactRequest(name, null, ContactType.PERSON, null)).id();
    }

    private void capitalIn(UUID partner, UUID account, long cents) {
        capitalService.move(partner, new CapitalRequests.Movement(CapitalRequests.Direction.IN, account, today, cents, null));
    }

    private void allocate(UUID partner, long cents) {
        capitalService.allocate(new CapitalRequests.Allocation(partner, today, cents, today.withDayOfMonth(1), today.withDayOfMonth(31)));
    }

    private UUID dealer(String plate, UUID seller, long actual, UUID account, List<InstallmentRequest> plan) {
        return create(request(plate, seller, actual, account, plan, Ownership.DEALER, null, null, null, null, null));
    }

    private UUID consignment(String plate, UUID owner, CommissionType type, Integer rateBp, Long fixedCents) {
        return create(request(plate, null, null, null, null, Ownership.CONSIGNMENT, owner, type, rateBp, fixedCents, null));
    }

    private UUID create(VehicleRequest r) {
        return vehicleService.create(r).vehicle().id();
    }

    /** Tek yerde: VehicleRequest alanlari degisirse sadece burasi degisir. */
    private VehicleRequest request(String plate, UUID seller, Long actual, UUID account, List<InstallmentRequest> plan,
                                   Ownership ownership, UUID consignor, CommissionType type, Integer rateBp, Long fixedCents,
                                   List<ShareRequest> shares) {
        return new VehicleRequest(plate, "Fiat", "Egea", (short) 2020, Fuel.GASOLINE, Transmission.MANUAL, 50_000, null, null, null,
                today, seller, null, actual, actual, account, plan, ownership, consignor, type, rateBp, fixedCents, shares, null, null);
    }

    private void expense(UUID vehicle, long cents, String category, Boolean chargeToOwner) {
        ledgerService.addExpense(new ExpenseRequest(today, cash, cents, vehicle, null, category, null, null, chargeToOwner));
    }

    private void sell(UUID vehicle, UUID buyer, long price, InstallmentRequest... rows) {
        vehicleService.sell(vehicle, new VehicleSaleRequest(today, buyer, null, price, List.of(rows)));
    }

    private InstallmentRequest paidNow(long cents, UUID account) {
        return new InstallmentRequest(today, cents, PaymentMethod.CASH, null, null, true, account);
    }

    private static InstallmentRequest note(LocalDate due, long cents) {
        return new InstallmentRequest(due, cents, PaymentMethod.PROMISSORY_NOTE, null, null, false, null);
    }

    private void collect(UUID installment, UUID account) {
        paymentPlanService.collect(installment, new CollectRequest(account, today));
    }

    private void pay(UUID contact, UUID account, long cents) {
        ledgerService.payContact(contact, new ContactPaymentRequest(account, today, cents, null));
    }

    // ------------------------------------------------------------------ okuma

    private long cashBalance() {
        return moneyBalance(cash);
    }

    private long bankBalance() {
        return moneyBalance(bank);
    }

    private long moneyBalance(UUID account) {
        return ledgerService.moneyAccounts().stream().filter(a -> a.id().equals(account)).findFirst().orElseThrow().balanceCents();
    }

    private VehicleCost cost(UUID vehicle) {
        return ledgerService.vehicleCost(vehicle);
    }

    private DashboardResponse dashboard() {
        return dashboardService.summary();
    }

    private CapitalResponses.PeriodProfit monthProfit() {
        return capitalService.periodProfit(today.withDayOfMonth(1), today.withDayOfMonth(31));
    }

    private CapitalResponses.PartnerProfit partnerProfit(CapitalResponses.PeriodProfit p, UUID partner) {
        return p.partners().stream().filter(x -> x.contactId().equals(partner)).findFirst().orElseThrow();
    }

    private CapitalResponses.Partner partner(UUID contact) {
        return capitalService.partners().stream().filter(p -> p.contactId().equals(contact)).findFirst().orElseThrow();
    }

    private List<Long> saleNotes(UUID vehicle) {
        return plan(vehicle, PlanDirection.SALE).installments().stream()
                .filter(i -> i.method() == PaymentMethod.PROMISSORY_NOTE).map(InstallmentResponse::amountCents).toList();
    }

    private PaymentPlanResponse plan(UUID vehicle, PlanDirection direction) {
        return paymentPlanService.plans(vehicle).stream().filter(p -> p.direction() == direction).findFirst().orElseThrow();
    }

    private UUID firstUnpaid(UUID vehicle, PlanDirection direction) {
        return plan(vehicle, direction).installments().stream()
                .filter(i -> i.status() != InstallmentStatus.PAID).findFirst().orElseThrow().id();
    }

    private List<InstallmentResponse> openInstallments(PlanDirection direction) {
        return paymentPlanService.list(null, direction, null, null).stream().filter(i -> i.status() != InstallmentStatus.PAID).toList();
    }

    private VehicleStatus vehicleStatus(UUID vehicle) {
        return VehicleStatus.valueOf(jdbc.queryForObject("select status from vehicle where id = ?", String.class, vehicle));
    }

    private UUID activeEntry(UUID vehicle, String type) {
        return jdbc.queryForObject("""
                select e.id from ledger_entry e
                where e.vehicle_id = ? and e.type = ? and e.corrected_entry_id is null
                  and not exists (select 1 from ledger_entry r where r.corrected_entry_id = e.id)
                limit 1
                """, UUID.class, vehicle, type);
    }

    // ------------------------------------------------------------------ beklentiler

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService).check(tenant, step);
    }

    private void expectMoney(String step, long cashCents, long bankCents) {
        assertThat(cashBalance()).as("%s: Kasa", step).isEqualTo(cashCents);
        assertThat(bankBalance()).as("%s: Ziraat", step).isEqualTo(bankCents);
    }

    private void expectCari(String step, UUID contact, long cents) {
        assertThat(paymentPlanService.statement(contact).balanceCents()).as("%s: cari", step).isEqualTo(cents);
    }

    private void expectCapital(String step, UUID contact, long cents) {
        assertThat(partner(contact).capitalCents()).as("%s: sermaye", step).isEqualTo(cents);
    }

    private void expectBound(String step, UUID contact, long cents) {
        assertThat(partner(contact).boundCents()).as("%s: araclarda bagli sermaye", step).isEqualTo(cents);
    }

    private void expectShare(String step, UUID contact, int bp) {
        assertThat(partner(contact).shareBp()).as("%s: pay (baz puan)", step).isEqualTo(bp);
    }

    private void expectSuggested(String step, CapitalResponses.PeriodProfit p, UUID partner, long cents) {
        assertThat(partnerProfit(p, partner).suggestedCents()).as("%s: onerilen kar payi", step).isEqualTo(cents);
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
