package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.EndorseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.OpenItemsRequest;
import com.galeri.app.model.response.ContactStatement;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
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

// Gecmis senet / cek aktarimi (aracsiz plan) gercek Postgres'te: cariye yazilir, tahsil / ciro edilir, hatali kayit silinir.
@SpringBootTest
class OpenItemsIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock openItemsClock() {
            return Clock.fixed(Instant.parse("2026-06-01T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private PaymentPlanService paymentPlanService;

    private final LocalDate today = LocalDate.of(2026, 6, 1);
    private UUID tenant, cash, ali, mehmet;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Gecmis')", tenant, "gecmis-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "95" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        UUID furkan = user;   // dukkan sahibi kullanici (V22)
        ali = person("Ali");
        mehmet = person("Mehmet");
        capitalService.move(furkan, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void gecmisAlacak_ucSenetCariyeYazilir_tahsilEdilir_aracYok() {
        PaymentPlanResponse plan = paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.SALE, today, List.of(
                note(today.plusDays(30), 200_000 * TL, "S-1"),
                note(today.minusDays(5), 200_000 * TL, "S-2"),
                cheque(today.plusDays(60), 200_000 * TL))));
        assertThat(plan.vehicleId()).isNull();
        assertThat(plan.totalCents()).isEqualTo(600_000 * TL);
        assertThat(plan.installments()).extracting(InstallmentResponse::dueDate)
                .containsExactly(today.minusDays(5), today.plusDays(30), today.plusDays(60));
        assertThat(plan.installments().getFirst().status()).isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(balance(ali)).isEqualTo(600_000 * TL);
        ContactStatement st = paymentPlanService.statement(ali);
        assertThat(st.openInstallments()).hasSize(3);
        assertThat(paymentPlanService.list(null, PlanDirection.SALE, null, null)).hasSize(3);
        check("aktarim");

        paymentPlanService.collect(plan.installments().getFirst().id(), new CollectRequest(cash, today));
        assertThat(balance(ali)).isEqualTo(400_000 * TL);
        assertThat(cashBalance()).isEqualTo(1_200_000 * TL);
        check("tahsil");
    }

    @Test
    void gecmisBorc_verilenSenetOdenir() {
        PaymentPlanResponse plan = paymentPlanService.addOpenItems(mehmet, new OpenItemsRequest(PlanDirection.PURCHASE, today, List.of(
                note(today.plusDays(10), 300_000 * TL, "V-1"))));
        assertThat(balance(mehmet)).isEqualTo(-300_000 * TL);
        check("borc");
        paymentPlanService.collect(plan.installments().getFirst().id(), new CollectRequest(cash, today.plusDays(10)));
        assertThat(balance(mehmet)).isZero();
        assertThat(cashBalance()).isEqualTo(700_000 * TL);
        check("odendi");
    }

    @Test
    void gecmisCek_gecmisBorcaCiroEdilir() {
        PaymentPlanResponse receivable = paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.SALE, today, List.of(
                cheque(today.plusDays(20), 150_000 * TL))));
        PaymentPlanResponse payable = paymentPlanService.addOpenItems(mehmet, new OpenItemsRequest(PlanDirection.PURCHASE, today, List.of(
                note(today.plusDays(15), 150_000 * TL, "V-2"))));
        paymentPlanService.endorse(receivable.installments().getFirst().id(),
                new EndorseRequest(mehmet, payable.installments().getFirst().id(), today));
        assertThat(balance(ali)).isZero();
        assertThat(balance(mehmet)).isZero();
        check("ciro");
    }

    @Test
    void hataliKayit_odenmemisseSilinir_odenmisseSilinmez_acilisHareketiKasadanDuzeltilmez() {
        PaymentPlanResponse plan = paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.SALE, today, List.of(
                note(today.plusDays(30), 100_000 * TL, "S-9"), note(today.plusDays(60), 100_000 * TL, "S-10"))));
        UUID opening = jdbc.queryForObject("select opening_entry_id from payment_plan where id = ?", UUID.class, plan.id());
        expectError(1023, () -> ledgerService.correct(opening));

        paymentPlanService.collect(plan.installments().getFirst().id(), new CollectRequest(cash, today));
        expectError(1052, () -> paymentPlanService.removeOpenItems(plan.id()));
        paymentPlanService.undo(plan.installments().getFirst().id());

        paymentPlanService.removeOpenItems(plan.id());
        assertThat(balance(ali)).isZero();
        assertThat(paymentPlanService.list(null, null, null, null)).isEmpty();
        check("silindi");

        // Kisiye ayrica Kasa'dan acilis bakiyesi girilebilir (gecmis senet kaydi engellemez)
        paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.SALE, today, List.of(note(today, 50_000 * TL, null))));
        ledgerService.openingBalance(new CapitalRequests.Opening(null, ali, CapitalRequests.OpeningSide.RECEIVABLE, today, 10_000 * TL));
        assertThat(balance(ali)).isEqualTo(60_000 * TL);
        check("acilis");
    }

    // ------------------------------------------------------------------ yardimcilar

    private UUID person(String name) {
        return contactService.create(new ContactRequest(name, null, ContactType.PERSON, null)).id();
    }

    private static InstallmentRequest note(LocalDate due, long cents, String no) {
        return new InstallmentRequest(due, cents, PaymentMethod.PROMISSORY_NOTE, no, null, false, null);
    }

    private static InstallmentRequest cheque(LocalDate due, long cents) {
        return new InstallmentRequest(due, cents, PaymentMethod.CHEQUE, "C-1", null, false, null, "Garanti", null);
    }

    private long balance(UUID contact) {
        Long b = jdbc.queryForObject("""
                select coalesce((select sum(e.amount_cents) from ledger_entry e where e.target_account_id = a.id), 0)
                     - coalesce((select sum(e.amount_cents) from ledger_entry e where e.source_account_id = a.id), 0)
                from account a where a.tenant_id = ? and a.type = 'BALANCE' and a.contact_id = ?
                """, Long.class, tenant, contact);
        return b == null ? 0 : b;
    }

    private long cashBalance() {
        return ledgerService.moneyAccounts().stream().filter(a -> a.id().equals(cash)).findFirst().orElseThrow().balanceCents();
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService).check(tenant, step);
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
