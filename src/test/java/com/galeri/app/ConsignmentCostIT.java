package com.galeri.app;

import com.galeri.app.exception.runtime.ConsignorCashNegativeException;
import com.galeri.app.exception.runtime.InsufficientCapitalException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.DashboardService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.NetWorthService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
import com.galeri.app.support.TestSession;
import com.galeri.app.support.TestUsers;
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

// Konsinye yeniden tasarimi (Kasim 2026, DURUM kural 1-4) gercek Postgres'te.
// Furkan dukkan sahibi (5.000.000 kasada), Zeki sermayedar (1.000.000 kasada). Dukkan sahibinin parasina hic dokunulmaz.
@SpringBootTest
class ConsignmentCostIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock consignmentCostClock() {
            return Clock.fixed(Instant.parse("2026-11-04T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private VehicleService vehicleService;
    @Autowired private ContactService contactService;
    @Autowired private DashboardService dashboardService;
    @Autowired private NetWorthService netWorthService;

    private final LocalDate today = LocalDate.of(2026, 11, 4);
    private UUID tenant, admin, zeki, cash, buyer;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Konsinye')", tenant, "kon-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                admin, tenant, "94" + suffix);
        TestSession.open(admin, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        zeki = TestUsers.investor(jdbc, tenant, "Zeki");
        buyer = contactService.create(new ContactRequest("Veli", null, ContactType.PERSON, null)).id();
        capitalService.move(admin, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 5_000_000 * TL, null));
        capitalService.move(zeki, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void sermayeden_masrafHemenSermayedenDuser_komisyonKarin3u_galeri6000_zeki194000() {
        UUID v = vehicleService.create(consignment(800_000 * TL, null, CommissionType.RATE, 300, null)).vehicle().id();
        assertThat(cashBalance()).as("alis kasadan").isEqualTo(5_200_000 * TL);
        expectZeki("alis", 1_000_000 * TL, 200_000 * TL, 800_000 * TL);
        expectFurkan("alis", 5_000_000 * TL);
        assertThat(dashboardService.summary().stockCostCents()).as("maliyet stokta").isEqualTo(800_000 * TL);
        check("alis");

        ledgerService.addExpense(expense(v, 20_000 * TL, null));
        assertThat(cashBalance()).isEqualTo(5_180_000 * TL);
        expectZeki("masraf: 1.000.000 -> 980.000", 980_000 * TL, 180_000 * TL, 800_000 * TL);
        expectFurkan("masraf", 5_000_000 * TL);
        assertThat(dashboardService.summary().stockCostCents()).as("masraf stok maliyetine girmez").isEqualTo(800_000 * TL);
        check("masraf");

        vehicleService.sell(v, sale(1_000_000 * TL));
        VehicleCost c = ledgerService.vehicleCost(v);
        assertThat(c.commissionCents()).as("komisyon = 200.000 karin %3'u").isEqualTo(6_000 * TL);
        assertThat(c.ownerPayoutCents()).isEqualTo(994_000 * TL);
        assertThat(c.profitCents()).as("galerinin kari").isEqualTo(6_000 * TL);
        assertThat(c.ownerChargedCents()).as("sahibine ait masraf").isEqualTo(20_000 * TL);
        expectZeki("satis: masraf bir daha dusmez, 194.000 kar", 1_174_000 * TL, 1_174_000 * TL, 0);
        expectFurkan("satis", 5_000_000 * TL);
        assertThat(cashBalance()).isEqualTo(6_180_000 * TL);
        assertThat(capitalService.periodProfit(today, today).commissionCents()).isEqualTo(6_000 * TL);
        check("satis");

        vehicleService.undoSale(v);
        expectZeki("satis geri alindi", 980_000 * TL, 180_000 * TL, 800_000 * TL);
        check("satis geri alindi");
    }

    @Test
    void hibritVeCepten_ceptenKisimSermayesineEklenir_kaldirilincaGeriDoner() {
        UUID v = vehicleService.create(consignment(800_000 * TL, 300_000 * TL, CommissionType.RATE, 300, null)).vehicle().id();
        assertThat(cashBalance()).as("kasadan sadece sermaye kismi").isEqualTo(5_500_000 * TL);
        expectZeki("hibrit", 1_300_000 * TL, 500_000 * TL, 800_000 * TL);
        check("hibrit");

        vehicleService.update(v, consignment(800_000 * TL, 800_000 * TL, CommissionType.RATE, 300, null));
        assertThat(cashBalance()).as("cepten: kasa net degismez").isEqualTo(6_000_000 * TL);
        expectZeki("cepten", 1_800_000 * TL, 1_000_000 * TL, 800_000 * TL);
        check("cepten");

        vehicleService.remove(v);
        assertThat(cashBalance()).isEqualTo(6_000_000 * TL);
        expectZeki("kaldirildi", 1_000_000 * TL, 1_000_000 * TL, 0);
        check("kaldirildi");
    }

    @Test
    void sermayedenAlis_nakdiYetmezseReddedilir() {
        assertThatThrownBy(() -> vehicleService.create(consignment(1_200_000 * TL, null, CommissionType.RATE, 300, null)))
                .isInstanceOf(InsufficientCapitalException.class);
    }

    @Test
    void masraf_nakdiYetmezseOnayIster_onaylanirsaEksiyeDuser() {
        UUID v = vehicleService.create(consignment(900_000 * TL, null, CommissionType.RATE, 300, null)).vehicle().id();
        expectZeki("alis", 1_000_000 * TL, 100_000 * TL, 900_000 * TL);

        assertThatThrownBy(() -> ledgerService.addExpense(expense(v, 150_000 * TL, null)))
                .isInstanceOf(ConsignorCashNegativeException.class);
        expectZeki("onaysiz: degismedi", 1_000_000 * TL, 100_000 * TL, 900_000 * TL);

        ledgerService.addExpense(expense(v, 150_000 * TL, true));
        expectZeki("onayli: eksiye dustu", 850_000 * TL, -50_000 * TL, 900_000 * TL);
        expectFurkan("onayli", 5_000_000 * TL);
        check("eksi");
    }

    @Test
    void zarardaKomisyonSifir_zararSahibinde() {
        UUID v = vehicleService.create(consignment(800_000 * TL, null, CommissionType.RATE, 300, null)).vehicle().id();
        vehicleService.sell(v, sale(700_000 * TL));
        assertThat(ledgerService.vehicleCost(v).commissionCents()).isZero();
        expectZeki("zarar", 900_000 * TL, 900_000 * TL, 0);
        expectFurkan("zarar", 5_000_000 * TL);
        check("zarar");
    }

    @Test
    void sabitKomisyon_kardanBuyukse_karKadarAlinir() {
        UUID v = vehicleService.create(consignment(997_000 * TL, 997_000 * TL, CommissionType.FIXED, null, 5_000 * TL)).vehicle().id();
        vehicleService.sell(v, sale(1_000_000 * TL));
        assertThat(ledgerService.vehicleCost(v).commissionCents()).isEqualTo(3_000 * TL);
        expectZeki("kar kadar", 1_997_000 * TL, 1_997_000 * TL, 0);   // 1.000.000 + 997.000 cepten + 0 net kar
        check("sabit");
    }

    @Test
    void maliyetBos_bugunkuGibi_komisyonSatisin3u() {
        UUID v = vehicleService.create(consignment(null, null, CommissionType.RATE, 300, null)).vehicle().id();
        assertThat(cashBalance()).isEqualTo(6_000_000 * TL);
        expectZeki("stokta", 1_000_000 * TL, 1_000_000 * TL, 0);
        vehicleService.sell(v, sale(1_000_000 * TL));
        assertThat(ledgerService.vehicleCost(v).commissionCents()).isEqualTo(30_000 * TL);
        expectZeki("satis", 1_970_000 * TL, 1_970_000 * TL, 0);
        check("maliyetsiz");
    }

    @Test
    void ceptenMasraf_sadeceGorunur_paraOynamaz() {
        UUID v = vehicleService.create(consignment(800_000 * TL, null, CommissionType.RATE, 300, null)).vehicle().id();
        ledgerService.addPocketExpense(new ExpenseRequest(today, null, 15_000 * TL, v, null, "Lastik", null, "Zeki odedi", null));
        assertThat(ledgerService.pocketExpenses(v)).singleElement()
                .satisfies(p -> assertThat(p.amountCents()).isEqualTo(15_000 * TL));
        assertThat(cashBalance()).isEqualTo(5_200_000 * TL);
        expectZeki("cepten masraf", 1_000_000 * TL, 200_000 * TL, 800_000 * TL);
        assertThat(ledgerService.vehicleCost(v).ownerChargedCents()).isZero();
        check("cepten masraf");

        ledgerService.deletePocketExpense(ledgerService.pocketExpenses(v).get(0).id());
        assertThat(ledgerService.pocketExpenses(v)).isEmpty();
    }

    private VehicleRequest consignment(Long cost, Long pocket, CommissionType type, Integer rateBp, Long fixed) {
        return new VehicleRequest("34 KN " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, null, null, cost, null, null, null, Ownership.CONSIGNMENT, zeki, type, rateBp, fixed,
                null, null, null, null, null, null, null, pocket);
    }

    private ExpenseRequest expense(UUID vehicle, long cents, Boolean confirmNegative) {
        return new ExpenseRequest(today, cash, cents, vehicle, null, "Bakim", null, null, confirmNegative);
    }

    private VehicleSaleRequest sale(long price) {
        return new VehicleSaleRequest(today, buyer, null, price,
                List.of(new InstallmentRequest(today, price, PaymentMethod.CASH, null, null, true, cash)));
    }

    private long cashBalance() {
        return jdbc.queryForObject("""
                select coalesce((select sum(amount_cents) from ledger_entry where target_account_id = ?), 0)
                     - coalesce((select sum(amount_cents) from ledger_entry where source_account_id = ?), 0)""", Long.class, cash, cash);
    }

    private void expectZeki(String step, long capital, long cashPart, long bound) {
        CapitalResponses.Partner p = capitalService.partners().stream().filter(x -> x.userId().equals(zeki)).findFirst().orElseThrow();
        assertThat(p.capitalCents()).as("%s: sermaye", step).isEqualTo(capital);
        assertThat(p.cashCents()).as("%s: nakitteki", step).isEqualTo(cashPart);
        assertThat(p.boundCents()).as("%s: aracta", step).isEqualTo(bound);
    }

    private void expectFurkan(String step, long capital) {
        CapitalResponses.Partner p = capitalService.partners().stream().filter(x -> x.userId().equals(admin)).findFirst().orElseThrow();
        assertThat(p.capitalCents()).as("%s: dukkan sahibinin sermayesi", step).isEqualTo(capital);
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
        assertThat(netWorthService.summary().differenceCents()).as("%s: denge", step).isZero();
    }
}
