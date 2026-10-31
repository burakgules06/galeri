package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.ContributionSource;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.EndorseRequest;
import com.galeri.app.model.request.OpenItemsRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.request.InstallmentRequest;
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

// Sermayedara ait gecmis evrak (V18) gercek Postgres'te: tahsil edildikce (ya da ciro edilince) onun sermayesine yazilir;
// odeme geri alinca / karsiliksiz cikinca terslenir. Dukkan sahibinin payi havuzdan kalan oldugu icin o kadar azalir.
@SpringBootTest
class HolderItemsIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock holderItemsClock() {
            return Clock.fixed(Instant.parse("2026-08-01T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long K = 1_000L * 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private NetWorthService netWorthService;
    @Autowired private PaymentPlanService paymentPlanService;

    private final LocalDate today = LocalDate.of(2026, 8, 1);
    private UUID tenant, cash, furkan, burak, ali, veli;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Evrak')", tenant, "evrak-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                user, tenant, "94" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        furkan = user;   // dukkan sahibi kullanici
        burak = TestUsers.investor(jdbc, tenant, "Burak Şahin");
        ali = person("Ali");
        veli = person("Veli");
        move(furkan, 10_000 * K);
        move(burak, 1_000 * K);
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void sermayedarinEvraki_alacakOlarakOnunKartinda_tahsilEdilinceNakde_geriAlinincaYineAlacak_sahibiEtkilenmez() {
        paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.SALE, today, List.of(
                cheque(500 * K, "C-1"), cheque(300 * K, "C-2")), burak));
        assertThat(paymentPlanService.list(null, PlanDirection.SALE, null, null))
                .allSatisfy(i -> assertThat(i.holder().id()).isEqualTo(burak));
        expect("evrak girildi", burak, 1_800 * K, 1_000 * K, 800 * K);   // alacak Burak'in: riski onda
        expect("evrak girildi", furkan, 10_000 * K, 10_000 * K, 0);
        expectError(1055, () -> capitalService.removePartner(burak));
        check("evrak girildi");

        UUID first = open().get(0), second = open().get(1);
        paymentPlanService.collect(first, new CollectRequest(cash, today));
        expect("tahsil", burak, 1_800 * K, 1_500 * K, 300 * K);
        expect("tahsil", furkan, 10_000 * K, 10_000 * K, 0);
        check("tahsil");

        paymentPlanService.undo(first);
        expect("geri alindi", burak, 1_800 * K, 1_000 * K, 800 * K);
        check("geri alindi");
        paymentPlanService.collect(first, new CollectRequest(cash, today));

        // Ciro: dukkan evraki kullandi, Burak'in nakdine gecer; karsiliksiz cikinca yine Burak'in alacagi
        paymentPlanService.endorse(second, new EndorseRequest(veli, null, today));
        expect("ciro", burak, 1_800 * K, 1_800 * K, 0);
        check("ciro");
        paymentPlanService.bounce(second);
        expect("karsiliksiz", burak, 1_800 * K, 1_500 * K, 300 * K);
        expect("karsiliksiz", furkan, 10_000 * K, 10_000 * K, 0);
        assertThat(holders().stream().mapToLong(CapitalResponses.Holder::capitalCents).sum())
                .as("herkesin sermayesi = havuz").isEqualTo(netWorthService.summary().totalCents());
        check("karsiliksiz");
    }

    @Test
    void sahibeYaDaOrtakOlmayanaEvrakYazilmaz_borcEvrakindaKimDikkateAlinmaz() {
        expectError(1035, () -> paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.SALE, today,
                List.of(cheque(100 * K, "C-3")), furkan)));
        expectError(1035, () -> paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.SALE, today,
                List.of(cheque(100 * K, "C-4")), TestUsers.add(jdbc, tenant, "Çalışan", "STAFF"))));
        paymentPlanService.addOpenItems(ali, new OpenItemsRequest(PlanDirection.PURCHASE, today, List.of(cheque(100 * K, "C-5")), burak));
        UUID item = paymentPlanService.list(null, PlanDirection.PURCHASE, null, null).getFirst().id();
        paymentPlanService.collect(item, new CollectRequest(cash, today));
        expect("borc odendi", burak, 1_000 * K, 1_000 * K, 0);
        check("borc");
    }

    // ------------------------------------------------------------------ yardimcilar

    private static InstallmentRequest cheque(long cents, String no) {
        return new InstallmentRequest(LocalDate.of(2026, 9, 1), cents, PaymentMethod.CHEQUE, no, null, false, null);
    }

    private List<UUID> open() {
        return paymentPlanService.list(null, PlanDirection.SALE, null, null).stream()
                .sorted((a, b) -> Long.compare(b.amountCents(), a.amountCents()))
                .map(i -> i.id()).toList();
    }

    private UUID person(String name) {
        return contactService.create(new ContactRequest(name, null, ContactType.PERSON, null)).id();
    }

    private void move(UUID contact, long cents) {
        capitalService.move(contact, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, cents, null));
    }

    private List<CapitalResponses.Holder> holders() {
        return netWorthService.holders();
    }

    private CapitalResponses.Holder holder(UUID contact) {
        return holders().stream().filter(h -> contact.equals(h.userId())).findFirst().orElseThrow();
    }

    private void expect(String step, UUID contact, long capital, long cashPart, long receivable) {
        CapitalResponses.Holder h = holder(contact);
        assertThat(h.capitalCents()).as("%s: sermaye", step).isEqualTo(capital);
        assertThat(h.cashCents()).as("%s: nakit", step).isEqualTo(cashPart);
        assertThat(h.otherCents()).as("%s: alacak", step).isEqualTo(receivable);
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
