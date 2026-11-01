package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
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
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.OwnerReturnRequest;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.NetWorthService;
import com.galeri.app.service.PaymentPlanService;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sermayedarin alim / satimi (MIMARI §0 adim 4) gercek Postgres'te. Burak sermayedar, 1.000.000 sermayesi kasada.
// Alimda payi kendi sermayesinden (kasa secmez), satista ve tahsilatta "Sermayeme ekle" ya da "Nakit aldım".
@SpringBootTest
class InvestorTradingIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock investorTradingClock() {
            return Clock.fixed(Instant.parse("2026-05-10T07:00:00Z"), ZoneOffset.UTC);
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
    @Autowired private NetWorthService netWorthService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, admin, burak, cash, buyer;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Sermayedar')", tenant, "inv-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                admin, tenant, "94" + suffix);
        asAdmin();
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        burak = TestUsers.investor(jdbc, tenant, "Burak");
        capitalService.move(admin, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 5_000_000 * TL, null));
        capitalService.move(burak, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
        buyer = contactService.create(new ContactRequest("Alıcı", null, ContactType.PERSON, null)).id();
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void sermayedarAlir_payiSermayesindenDuser_satarSermayesineEkler() {
        asInvestor();
        // Kasa gonderse de yok sayilir; senetli alis olmaz
        UUID v = vehicleService.create(request(600_000 * TL, List.of(new ShareRequest(burak, 10000)))).vehicle().id();
        assertThatThrownBy(() -> vehicleService.create(new VehicleRequest("34 IV 999", "Fiat", "Egea", (short) 2022, Fuel.GASOLINE,
                Transmission.MANUAL, 1, null, null, null, today, null, null, 100_000 * TL, 100_000 * TL, null,
                List.of(new InstallmentRequest(today.plusDays(30), 100_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null)),
                Ownership.CO_OWNED, null, null, null, null, List.of(new ShareRequest(burak, 10000)), null, null)))
                .isInstanceOf(ApplicationRuntimeException.class);
        expectPartner("alis", 1_000_000 * TL, 400_000 * TL, 600_000 * TL);
        assertThat(cashBalance()).as("kasadan cikti").isEqualTo(5_400_000 * TL);

        vehicleService.sell(v, new VehicleSaleRequest(today, buyer, null, 700_000 * TL,
                List.of(new InstallmentRequest(today, 700_000 * TL, PaymentMethod.CASH, null, null, true, null)), List.of()));
        expectPartner("satis: sermayeme ekle", 1_100_000 * TL, 1_100_000 * TL, 0);
        assertThat(cashBalance()).isEqualTo(6_100_000 * TL);
        asAdmin();
        check("satis");
    }

    @Test
    void satistaNakitAldim_payiCebine_kasaVeSermayeDegismez() {
        asInvestor();
        UUID v = vehicleService.create(request(600_000 * TL, List.of(new ShareRequest(burak, 10000)))).vehicle().id();
        vehicleService.sell(v, new VehicleSaleRequest(today, buyer, null, 700_000 * TL,
                List.of(new InstallmentRequest(today, 700_000 * TL, PaymentMethod.CASH, null, null, true, null)),
                List.of(new OwnerReturnRequest(burak, OwnerReturnMode.CASH, null, null))));
        expectPartner("nakit aldim", 400_000 * TL, 400_000 * TL, 0);   // 700.000 cebine
        assertThat(cashBalance()).isEqualTo(5_400_000 * TL);
        asAdmin();
        check("nakit aldim");
    }

    @Test
    void senetTahsilinde_nakitAldim_payiCekilir_yoneticiGeriAlirsaGeriDoner() {
        asInvestor();
        UUID v = vehicleService.create(request(600_000 * TL, List.of(new ShareRequest(burak, 10000)))).vehicle().id();
        vehicleService.sell(v, new VehicleSaleRequest(today, buyer, null, 700_000 * TL,
                List.of(new InstallmentRequest(today.plusDays(30), 700_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null))));
        expectPartner("senetle satis", 400_000 * TL, 400_000 * TL, 0);   // katki satisa gecti, 700.000 alacak
        UUID note = paymentPlanService.plans(v).stream().filter(p -> p.direction() == PlanDirection.SALE).findFirst().orElseThrow()
                .installments().getFirst().id();
        paymentPlanService.collect(note, new CollectRequest(null, today, null, true));
        expectPartner("tahsil, nakit aldim", 400_000 * TL, 400_000 * TL, 0);
        assertThat(cashBalance()).as("kasa net degismez").isEqualTo(5_400_000 * TL);

        asAdmin();
        check("tahsil");
        paymentPlanService.undo(note);
        assertThat(paymentPlanService.plans(v).stream().filter(p -> p.direction() == PlanDirection.SALE).findFirst().orElseThrow()
                .installments().getFirst().status()).isNotEqualTo(InstallmentStatus.PAID);
        expectPartner("geri alindi", 400_000 * TL, 400_000 * TL, 0);
        check("geri alindi");
    }

    @Test
    void sermayedar_baskasininAraciniSatamaz_vePlaninaTahsilatYapamaz() {
        UUID dealer = vehicleService.create(new VehicleRequest("34 IV 100", "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                1, null, null, null, today, null, null, 300_000 * TL, 300_000 * TL, cash, null, null, null, null, null, null, null, null, null))
                .vehicle().id();
        vehicleService.sell(dealer, new VehicleSaleRequest(today, buyer, null, 400_000 * TL,
                List.of(new InstallmentRequest(today.plusDays(30), 400_000 * TL, PaymentMethod.PROMISSORY_NOTE, null, null, false, null))));
        UUID note = paymentPlanService.plans(dealer).stream().filter(p -> p.direction() == PlanDirection.SALE).findFirst().orElseThrow()
                .installments().getFirst().id();
        UUID stock = vehicleService.create(new VehicleRequest("34 IV 101", "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                1, null, null, null, today, null, null, 300_000 * TL, 300_000 * TL, cash, null, null, null, null, null, null, null, null, null))
                .vehicle().id();

        asInvestor();
        assertThatThrownBy(() -> vehicleService.sell(stock, new VehicleSaleRequest(today, buyer, null, 400_000 * TL,
                List.of(new InstallmentRequest(today, 400_000 * TL, PaymentMethod.CASH, null, null, true, null)))))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> paymentPlanService.collect(note, new CollectRequest(null, today))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> vehicleService.requireAccess(stock)).isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ yardimcilar

    private VehicleRequest request(long price, List<ShareRequest> shares) {
        return new VehicleRequest("34 IV " + (200 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, null, null, price, price, UUID.randomUUID(), null, Ownership.CO_OWNED, null, null, null,
                null, shares, null, null);
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

    private void expectPartner(String step, long capital, long cashPart, long bound) {
        TestSession.close();
        TestSession.open(admin, tenant, "ADMIN");
        try {
            CapitalResponses.Partner p = capitalService.partners().stream().filter(x -> x.userId().equals(burak)).findFirst().orElseThrow();
            assertThat(p.capitalCents()).as("%s: sermaye", step).isEqualTo(capital);
            assertThat(p.cashCents()).as("%s: nakitteki", step).isEqualTo(cashPart);
            assertThat(p.boundCents()).as("%s: aracta", step).isEqualTo(bound);
        } finally {
            TestSession.close();
            TestSession.open(burak, tenant, "INVESTOR");
        }
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
        assertThat(netWorthService.summary().differenceCents()).as("%s: denge", step).isZero();
    }
}
