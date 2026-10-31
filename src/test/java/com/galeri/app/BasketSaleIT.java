package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.BasketSaleRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
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

// Toplu satis (sepet) gercek Postgres'te: ayni aliciya uc arac, toplam uzerinden tek plan; kar arac basina; sepet birlikte geri alinir.
@SpringBootTest
class BasketSaleIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock basketClock() {
            return Clock.fixed(Instant.parse("2026-07-01T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private VehicleService vehicleService;
    @Autowired private PaymentPlanService paymentPlanService;

    private final LocalDate today = LocalDate.of(2026, 7, 1);
    private UUID tenant, cash, ali, eminevim;
    private UUID v1, v2, v3;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Sepet')", tenant, "sepet-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "96" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        UUID furkan = user;   // dukkan sahibi kullanici (V22)
        ali = contact("Ali", ContactType.PERSON);
        eminevim = contact("Eminevim", ContactType.ORGANIZATION);
        capitalService.move(furkan, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 5_000_000 * TL, null));
        v1 = vehicle(500_000 * TL);
        v2 = vehicle(600_000 * TL);
        v3 = vehicle(700_000 * TL);
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void ucAracTekPlan_karAracBasina_taksitlerToplamUzerinden_sepetBirlikteGeriAlinir() {
        vehicleService.sellBasket(new BasketSaleRequest(today, ali, null, items(600_000, 700_000, 900_000), List.of(
                new InstallmentRequest(today, 400_000 * TL, PaymentMethod.CASH, null, null, true, cash),
                note(today.plusMonths(1), 600_000 * TL), note(today.plusMonths(2), 600_000 * TL), note(today.plusMonths(3), 600_000 * TL))));

        for (UUID v : List.of(v1, v2, v3)) assertThat(status(v)).isEqualTo(VehicleStatus.SOLD);
        assertThat(saleTotal(v1)).isEqualTo(600_000 * TL);
        assertThat(saleTotal(v2)).isEqualTo(700_000 * TL);
        assertThat(saleTotal(v3)).isEqualTo(900_000 * TL);
        assertThat(balance(ali)).isEqualTo(1_800_000 * TL);
        assertThat(capitalService.periodProfit(today.withDayOfMonth(1), today.withDayOfMonth(31)).vehicleProfitCents())
                .as("kar arac basina: 100.000 + 100.000 + 200.000").isEqualTo(400_000 * TL);

        PaymentPlanResponse plan = paymentPlanService.plans(v2).stream().filter(p -> p.direction() == PlanDirection.SALE).findFirst().orElseThrow();
        assertThat(plan.totalCents()).isEqualTo(2_200_000 * TL);
        assertThat(plan.basket()).hasSize(3);
        assertThat(plan.installments()).hasSize(4);
        assertThat(paymentPlanService.list(null, PlanDirection.SALE, null, null)).allSatisfy(i -> assertThat(i.vehicle()).isNotNull());
        check("satis");

        InstallmentResponse firstNote = plan.installments().stream().filter(i -> i.status() != InstallmentStatus.PAID).findFirst().orElseThrow();
        paymentPlanService.collect(firstNote.id(), new CollectRequest(cash, today));
        assertThat(balance(ali)).isEqualTo(1_200_000 * TL);
        check("tahsil");

        expectError(1018, () -> vehicleService.undoSale(v1));
        plan.installments().stream().filter(i -> i.status() == InstallmentStatus.PAID).forEach(i -> paymentPlanService.undo(i.id()));
        paymentPlanService.undo(firstNote.id());

        vehicleService.undoSale(v3);   // herhangi bir araçtan: sepetin tamami
        for (UUID v : List.of(v1, v2, v3)) assertThat(status(v)).isEqualTo(VehicleStatus.IN_STOCK);
        assertThat(balance(ali)).isZero();
        assertThat(paymentPlanService.plans(v1)).isEmpty();
        assertThat(paymentPlanService.list(null, null, null, null)).isEmpty();
        check("geri alindi");

        // Ayni araclar yeniden (bu kez tek tek) satilabilir
        vehicleService.sellBasket(new BasketSaleRequest(today, ali, null, items(600_000, 700_000, 900_000), List.of(
                new InstallmentRequest(today, 2_200_000 * TL, PaymentMethod.CASH, null, null, true, cash))));
        check("yeniden");
    }

    @Test
    void kurumKismi_selaleIleAraclaraDagilir_herAracinToplamiBedeline_esit() {
        vehicleService.sellBasket(new BasketSaleRequest(today, ali, null, items(600_000, 700_000), List.of(
                new InstallmentRequest(today, 300_000 * TL, PaymentMethod.CASH, null, null, true, cash),
                new InstallmentRequest(today.plusDays(10), 1_000_000 * TL, PaymentMethod.INSTITUTION, null, eminevim, false, null))));
        assertThat(saleTotal(v1)).isEqualTo(600_000 * TL);
        assertThat(saleTotal(v2)).isEqualTo(700_000 * TL);
        assertThat(balance(ali)).isZero();
        assertThat(balance(eminevim)).isEqualTo(1_000_000 * TL);
        check("kurum");
    }

    @Test
    void kurallar_tekArac_ayniArac_toplamTutmazsa_reddedilir() {
        expectError(1053, () -> vehicleService.sellBasket(new BasketSaleRequest(today, ali, null,
                List.of(new BasketSaleRequest.Item(v1, 1L)), List.of(cashRow(1L)))));
        expectError(1053, () -> vehicleService.sellBasket(new BasketSaleRequest(today, ali, null,
                List.of(new BasketSaleRequest.Item(v1, 1L), new BasketSaleRequest.Item(v1, 1L)), List.of(cashRow(2L)))));
        expectError(1017, () -> vehicleService.sellBasket(new BasketSaleRequest(today, ali, null, items(600_000, 700_000),
                List.of(cashRow(1_000_000 * TL)))));
        assertThat(status(v1)).isEqualTo(VehicleStatus.IN_STOCK);
        check("kurallar");
    }

    // ------------------------------------------------------------------ yardimcilar

    private List<BasketSaleRequest.Item> items(long... prices) {
        List<UUID> ids = List.of(v1, v2, v3);
        return java.util.stream.IntStream.range(0, prices.length)
                .mapToObj(k -> new BasketSaleRequest.Item(ids.get(k), prices[k] * TL)).toList();
    }

    private InstallmentRequest cashRow(long cents) {
        return new InstallmentRequest(today, cents, PaymentMethod.CASH, null, null, true, cash);
    }

    private static InstallmentRequest note(LocalDate due, long cents) {
        return new InstallmentRequest(due, cents, PaymentMethod.PROMISSORY_NOTE, null, null, false, null);
    }

    private UUID contact(String name, ContactType type) {
        return contactService.create(new ContactRequest(name, null, type, null)).id();
    }

    private UUID vehicle(long price) {
        return vehicleService.create(new VehicleRequest("34 SP " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE,
                Transmission.MANUAL, 10_000, null, null, null, today, null, null, price, price, cash, null, null, null, null, null, null,
                null, null, null, null, null, null)).vehicle().id();
    }

    private VehicleStatus status(UUID vehicle) {
        return ((com.galeri.app.model.response.VehicleDetailAdmin) vehicleService.detail(vehicle)).vehicle().status();
    }

    /** Aracin gecerli satis hareketleri toplami (terslenenler duser). */
    private long saleTotal(UUID vehicle) {
        Long sum = jdbc.queryForObject("""
                select coalesce(sum(case when a.type = 'INCOME' then e.amount_cents else -e.amount_cents end), 0)
                from ledger_entry e join account a on a.id = e.source_account_id
                where e.vehicle_id = ? and e.type = 'VEHICLE_SALE'
                """, Long.class, vehicle);
        return sum == null ? 0 : sum;
    }

    private long balance(UUID contact) {
        Long b = jdbc.queryForObject("""
                select coalesce((select sum(e.amount_cents) from ledger_entry e where e.target_account_id = a.id), 0)
                     - coalesce((select sum(e.amount_cents) from ledger_entry e where e.source_account_id = a.id), 0)
                from account a where a.tenant_id = ? and a.type = 'BALANCE' and a.contact_id = ?
                """, Long.class, tenant, contact);
        return b == null ? 0 : b;
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService).check(tenant, step);
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
