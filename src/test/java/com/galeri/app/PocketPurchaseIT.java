package com.galeri.app;

import com.galeri.app.exception.runtime.PocketPurchaseNotAllowedException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.NetWorthService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
import com.galeri.app.support.TestSession;
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

// Dukkan sahibinin araci cebinden almasi (V31) gercek Postgres'te: cepten kisim kasadan cikmaz, sahibinin sermayesine eklenir.
// Furkan dukkan sahibi, kasada 5.000.000.
@SpringBootTest
class PocketPurchaseIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock pocketPurchaseClock() {
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
    private UUID tenant, admin, cash, seller;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = UUID.randomUUID();
        seller = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Cepten')", tenant, "cep-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                admin, tenant, "94" + suffix);
        jdbc.update("insert into contact (id, tenant_id, name) values (?, ?, 'Satici Ali')", seller, tenant);
        TestSession.open(admin, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        capitalService.move(admin, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 5_000_000 * TL, null));
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void tamamiCepten_kasaDegismez_sahibininSermayesiArtar_maliyetTamBedel() {
        UUID v = vehicleService.create(dealer(400_000 * TL, cash, 400_000 * TL, null)).vehicle().id();

        assertThat(cashBalance()).as("kasadan bir sey cikmaz").isEqualTo(5_000_000 * TL);
        assertThat(ownerCapital()).as("sermayesine eklendi").isEqualTo(5_400_000 * TL);
        assertThat(vehicleService.detail(v)).isInstanceOfSatisfying(VehicleDetailAdmin.class,
                d -> assertThat(d.pocketCents()).isEqualTo(400_000 * TL));
        assertThat(vehicleCost(v)).as("arac maliyeti tam bedel").isEqualTo(400_000 * TL);
        check("tamami cepten");
    }

    @Test
    void bolunmus_kalaniKasadan_degisinceVeKaldirilinca_herSeyGeriDoner() {
        UUID v = vehicleService.create(dealer(1_000_000 * TL, cash, 300_000 * TL, null)).vehicle().id();
        assertThat(cashBalance()).as("kasadan sadece kalan").isEqualTo(4_300_000 * TL);
        assertThat(ownerCapital()).isEqualTo(5_300_000 * TL);
        assertThat(vehicleCost(v)).isEqualTo(1_000_000 * TL);
        check("bolunmus");

        vehicleService.update(v, dealer(1_000_000 * TL, cash, 500_000 * TL, null));
        assertThat(cashBalance()).isEqualTo(4_500_000 * TL);
        assertThat(ownerCapital()).isEqualTo(5_500_000 * TL);
        assertThat(vehicleCost(v)).isEqualTo(1_000_000 * TL);
        check("cepten kisim artti");

        vehicleService.update(v, dealer(1_000_000 * TL, cash, 0L, null));
        assertThat(cashBalance()).as("cepten kaldirildi: tamami kasadan").isEqualTo(4_000_000 * TL);
        assertThat(ownerCapital()).isEqualTo(5_000_000 * TL);
        check("cepten kaldirildi");

        vehicleService.update(v, dealer(1_000_000 * TL, cash, 1_000_000 * TL, null));
        vehicleService.remove(v);
        assertThat(cashBalance()).isEqualTo(5_000_000 * TL);
        assertThat(ownerCapital()).as("arac kaldirilinca sermaye eski haline").isEqualTo(5_000_000 * TL);
        check("kaldirildi");
    }

    @Test
    void ceptenKisimBedeliAsamaz_veOdemePlaniylaKullanilmaz() {
        assertThatThrownBy(() -> vehicleService.create(dealer(400_000 * TL, cash, 500_000 * TL, null)))
                .isInstanceOf(PocketPurchaseNotAllowedException.class);
        List<InstallmentRequest> plan = List.of(new InstallmentRequest(today.plusMonths(1), 300_000 * TL, PaymentMethod.PROMISSORY_NOTE,
                "S-1", null, false, null, null, null, null));
        assertThatThrownBy(() -> vehicleService.create(dealer(400_000 * TL, null, 100_000 * TL, plan)))
                .isInstanceOf(PocketPurchaseNotAllowedException.class);
        assertThat(cashBalance()).isEqualTo(5_000_000 * TL);
        assertThat(ownerCapital()).isEqualTo(5_000_000 * TL);
    }

    private VehicleRequest dealer(long price, UUID account, Long pocket, List<InstallmentRequest> plan) {
        return new VehicleRequest("34 CP " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, seller, null, price, price, account, plan, Ownership.DEALER, null, null, null, null,
                null, null, null, null, null, null, null, null, pocket);
    }

    private long cashBalance() {
        return balance(cash);
    }

    private long vehicleCost(UUID vehicleId) {
        UUID account = jdbc.queryForObject("select id from account where tenant_id = ? and type = 'VEHICLE' and vehicle_id = ?",
                UUID.class, tenant, vehicleId);
        return balance(account);
    }

    private long balance(UUID account) {
        return jdbc.queryForObject("""
                select coalesce((select sum(amount_cents) from ledger_entry where target_account_id = ?), 0)
                     - coalesce((select sum(amount_cents) from ledger_entry where source_account_id = ?), 0)""", Long.class, account, account);
    }

    private long ownerCapital() {
        CapitalResponses.Partner p = capitalService.partners().stream().filter(x -> x.userId().equals(admin)).findFirst().orElseThrow();
        return p.capitalCents();
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
        assertThat(netWorthService.summary().differenceCents()).as("%s: denge", step).isZero();
    }
}
