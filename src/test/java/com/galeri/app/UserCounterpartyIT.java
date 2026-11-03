package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.service.CapitalService;
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

// Panel kullanicisiyla alis / satis ve "satmak icin al" devri (V25, MIMARI §0 madde 5) gercek Postgres'te.
// Furkan dukkan sahibi (5.000.000 kasada), Burak sermayedar (1.000.000 kasada).
@SpringBootTest
class UserCounterpartyIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock userCounterpartyClock() {
            return Clock.fixed(Instant.parse("2026-05-10T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private VehicleService vehicleService;
    @Autowired private NetWorthService netWorthService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, admin, burak, cash;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Devir')", tenant, "dev-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                admin, tenant, "93" + suffix);
        asAdmin();
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        burak = TestUsers.investor(jdbc, tenant, "Burak");
        capitalService.move(admin, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 5_000_000 * TL, null));
        capitalService.move(burak, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void kullanicidanAlis_nakitKismiKasadan_sermayeKismiSermayesine_degisirVeKaldirilinca_geriDoner() {
        UUID v = vehicleService.create(dealer(300_000 * TL, burak, 200_000 * TL)).vehicle().id();
        assertThat(cashBalance()).as("kasadan sadece nakit kismi").isEqualTo(5_900_000 * TL);
        expectBurak("alis", 1_200_000 * TL, 1_200_000 * TL, 0);
        assertThat(vehicleService.detail(v)).isInstanceOfSatisfying(VehicleDetailAdmin.class,
                d -> assertThat(d.sellerUser().id()).isEqualTo(burak));
        check("alis");

        vehicleService.update(v, dealer(300_000 * TL, burak, 100_000 * TL));
        assertThat(cashBalance()).isEqualTo(5_800_000 * TL);
        expectBurak("sermaye kismi azaldi", 1_100_000 * TL, 1_100_000 * TL, 0);
        check("guncelleme");

        vehicleService.remove(v);
        assertThat(cashBalance()).isEqualTo(6_000_000 * TL);
        expectBurak("kaldirildi", 1_000_000 * TL, 1_000_000 * TL, 0);
        check("kaldirildi");
    }

    @Test
    void dukkanSahibindenAlis_sermayeKismiOlmaz() {
        assertThatThrownBy(() -> vehicleService.create(dealer(300_000 * TL, admin, 100_000 * TL)))
                .isInstanceOf(ApplicationRuntimeException.class);
    }

    @Test
    void satmakIcinAl_galeriAraciSermayedara_karDukkanaKalir_aracOnunStogunaGirer_geriAlininca_eskiHaline() {
        UUID v = vehicleService.create(dealer(500_000 * TL, null, null)).vehicle().id();
        assertThat(cashBalance()).isEqualTo(5_500_000 * TL);

        // 600.000: 400.000 sermayesinden, 200.000 nakit getirdi
        VehicleDetailAdmin sold = vehicleService.sell(v, toUser(600_000 * TL, burak, true, 400_000 * TL));
        assertThat(sold.buyerUser().id()).isEqualTo(burak);
        UUID copy = sold.transferToVehicleId();
        assertThat(copy).isNotNull();
        VehicleDetailAdmin t = (VehicleDetailAdmin) vehicleService.detail(copy);
        assertThat(t.vehicle().status()).isEqualTo(VehicleStatus.IN_STOCK);
        assertThat(t.transferFromVehicleId()).isEqualTo(v);
        assertThat(t.actualPurchaseCents()).isEqualTo(600_000 * TL);
        assertThat(cashBalance()).as("kasaya sadece getirdigi nakit girdi").isEqualTo(5_700_000 * TL);
        expectBurak("devir", 1_200_000 * TL, 600_000 * TL, 600_000 * TL);   // nakitteki: 1.000.000 - 400.000
        check("devir");

        assertThatThrownBy(() -> vehicleService.remove(copy)).isInstanceOf(ApplicationRuntimeException.class);

        vehicleService.undoSale(v);
        assertThat(cashBalance()).isEqualTo(5_500_000 * TL);
        expectBurak("geri alindi", 1_000_000 * TL, 1_000_000 * TL, 0);
        check("geri alindi");
    }

    @Test
    void satmakIcinAl_sermayedarAraciDukkanSahibine_bedelVeKariSermayesine() {
        asInvestor();
        UUID v = vehicleService.create(new VehicleRequest(plate(), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, null, null, 600_000 * TL, 600_000 * TL, null, null, Ownership.CO_OWNED, null, null,
                null, null, List.of(new ShareRequest(burak, 10000)), null, null)).vehicle().id();
        asAdmin();
        expectBurak("alis", 1_000_000 * TL, 400_000 * TL, 600_000 * TL);

        vehicleService.sell(v, toUser(700_000 * TL, admin, true, 0L));
        expectBurak("devir", 1_100_000 * TL, 1_100_000 * TL, 0);
        assertThat(cashBalance()).as("bedel havuzdan: kasa net degismez").isEqualTo(5_400_000 * TL);
        check("devir");
    }

    @Test
    void devirsizSatis_sermayeKismiSermayesindenDuser_nakitKasaya() {
        UUID v = vehicleService.create(dealer(500_000 * TL, null, null)).vehicle().id();
        vehicleService.sell(v, toUser(600_000 * TL, burak, false, 450_000 * TL));
        expectBurak("satis", 550_000 * TL, 550_000 * TL, 0);
        assertThat(cashBalance()).isEqualTo(5_650_000 * TL);   // 5.500.000 + 150.000 nakit
        check("satis");

        vehicleService.undoSale(v);
        expectBurak("geri alindi", 1_000_000 * TL, 1_000_000 * TL, 0);
        check("geri alindi");
    }

    @Test
    void dukkanSahibine_devirsizSatis_veBedeliAsanSermaye_reddedilir() {
        UUID v = vehicleService.create(dealer(500_000 * TL, null, null)).vehicle().id();
        assertThatThrownBy(() -> vehicleService.sell(v, toUser(600_000 * TL, admin, false, 0L))).isInstanceOf(ApplicationRuntimeException.class);
        assertThatThrownBy(() -> vehicleService.sell(v, toUser(600_000 * TL, burak, false, 700_000 * TL))).isInstanceOf(ApplicationRuntimeException.class);
    }

    private VehicleRequest dealer(long price, UUID sellerUser, Long sellerCapital) {
        return new VehicleRequest("34 UC " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, null, null, price, price, cash, null, null, null, null, null, null, null, null, null,
                null, null, null, sellerUser, sellerCapital);
    }

    private String plate() {
        return "34 UC " + (100 + ++plates);
    }

    private VehicleSaleRequest toUser(long price, UUID user, boolean transfer, Long capital) {
        return new VehicleSaleRequest(today, null, null, price, null, null, user, transfer, capital, cash);
    }

    private void asAdmin() {
        TestSession.close();
        TestSession.open(admin, tenant, "ADMIN");
    }

    private void asInvestor() {
        TestSession.close();
        TestSession.open(burak, tenant, "INVESTOR");
    }

    private long cashBalance() {
        return jdbc.queryForObject("""
                select coalesce((select sum(amount_cents) from ledger_entry where target_account_id = ?), 0)
                     - coalesce((select sum(amount_cents) from ledger_entry where source_account_id = ?), 0)""", Long.class, cash, cash);
    }

    private void expectBurak(String step, long capital, long cashPart, long bound) {
        CapitalResponses.Partner p = capitalService.partners().stream().filter(x -> x.userId().equals(burak)).findFirst().orElseThrow();
        assertThat(p.capitalCents()).as("%s: sermaye", step).isEqualTo(capital);
        assertThat(p.cashCents()).as("%s: nakitteki", step).isEqualTo(cashPart);
        assertThat(p.boundCents()).as("%s: aracta", step).isEqualTo(bound);
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
        assertThat(netWorthService.summary().differenceCents()).as("%s: denge", step).isZero();
    }
}
