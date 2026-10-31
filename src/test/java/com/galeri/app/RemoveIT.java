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

// Kaldirma (V17) gercek Postgres'te: yanlis girilen arac butun hareketleri terslenerek kalkar (para geldigi yere doner);
// ortak kaldirilinca nakitteki parasi girdigi hesaplara iade edilir, kalan kasadan; sermaye hesabi kapanir.
@SpringBootTest
class RemoveIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock removeClock() {
            return Clock.fixed(Instant.parse("2026-08-01T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long M = 1_000_000L * 100;
    private static final long K = 1_000L * 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private VehicleService vehicleService;
    @Autowired private NetWorthService netWorthService;
    @Autowired private PaymentPlanService paymentPlanService;

    private final LocalDate today = LocalDate.of(2026, 8, 1);
    private UUID tenant, cash, bank, furkan, burak, veli;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Kaldir')", tenant, "kaldir-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                user, tenant, "95" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        bank = ledgerService.addBank(new AccountRequest("Ziraat")).id();
        furkan = user;   // dukkan sahibi kullanici
        burak = TestUsers.investor(jdbc, tenant, "Burak Şahin");
        veli = person("Veli");
        move(furkan, cash, CapitalRequests.Direction.IN, 50 * M);
        move(burak, cash, CapitalRequests.Direction.IN, 4 * M);
        move(burak, bank, CapitalRequests.Direction.IN, 2 * M);
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void yanlisGirilenArac_hareketleriTerslenir_paraGeldigiYereDoner_listelerdenKalkar() {
        // Galeri araci + masrafi
        UUID dealer = vehicle(20 * M, null, null);
        ledgerService.addExpense(new ExpenseRequest(today, cash, 100 * K, dealer, null, "Boya", null, null, null));
        assertThat(balance(cash)).isEqualTo(33_900 * K);
        vehicleService.remove(dealer);
        assertThat(balance(cash)).as("alis ve masraf kasaya doner").isEqualTo(54 * M);
        assertThat(status(dealer)).isEqualTo(VehicleStatus.REMOVED);
        assertThat(vehicleService.search(null, null)).noneMatch(v -> v.id().equals(dealer));
        expectError(1008, () -> vehicleService.detail(dealer));
        expectError(1008, () -> vehicleService.remove(dealer));
        check("galeri araci");

        // Ortak arac: Burak %80 sermayeden; kaldirilinca parasi nakde doner
        UUID shared = vehicle(5 * M, null, List.of(new ShareRequest(furkan, 2000), new ShareRequest(burak, 8000, null, ContributionSource.CAPITAL, null)));
        assertThat(holder(burak).vehicleCents()).isEqualTo(4 * M);
        vehicleService.remove(shared);
        assertThat(holder(burak).cashCents()).isEqualTo(6 * M);
        assertThat(holder(burak).vehicleCents()).isZero();
        assertThat(balance(cash)).isEqualTo(54 * M);
        check("ortak arac");

        // Senetli alis: sadece pesinat odenmisse kaldirilir, satici borcu sifirlanir
        UUID planned = vehicle(3 * M, List.of(
                new InstallmentRequest(today, 1 * M, PaymentMethod.CASH, null, null, true, cash),
                new InstallmentRequest(today.plusMonths(1), 2 * M, PaymentMethod.PROMISSORY_NOTE, null, null, false, null)), null);
        assertThat(balance(cash)).isEqualTo(53 * M);
        vehicleService.remove(planned);
        assertThat(balance(cash)).isEqualTo(54 * M);
        assertThat(contactBalance(veli)).isZero();
        assertThat(paymentPlanService.list(null, PlanDirection.PURCHASE, null, null)).isEmpty();
        check("senetli");

        // Pesinat disinda odenmis senet varsa kaldirilmaz
        UUID paidNote = vehicle(3 * M, List.of(
                new InstallmentRequest(today, 1 * M, PaymentMethod.PROMISSORY_NOTE, null, null, false, null),
                new InstallmentRequest(today.plusMonths(1), 2 * M, PaymentMethod.PROMISSORY_NOTE, null, null, false, null)), null);
        UUID note = paymentPlanService.list(null, PlanDirection.PURCHASE, null, null).stream()
                .filter(i -> i.status() != InstallmentStatus.PAID).findFirst().orElseThrow().id();
        paymentPlanService.collect(note, new CollectRequest(cash, today));
        expectError(1054, () -> vehicleService.remove(paidNote));
        paymentPlanService.undo(note);
        vehicleService.remove(paidNote);
        check("odenmis senet");

        // Satilmis arac kaldirilmaz
        UUID sold = vehicle(1 * M, null, null);
        vehicleService.sell(sold, new VehicleSaleRequest(today, veli, null, 2 * M,
                List.of(new InstallmentRequest(today, 2 * M, PaymentMethod.CASH, null, null, true, cash))));
        expectError(1054, () -> vehicleService.remove(sold));
        check("satilmis");
    }

    @Test
    void ortakKaldirilir_paraGirdigiHesaplaraIadeEdilir_kalanKasadan_hesapKapanir() {
        capitalService.transferCapital(new CapitalRequests.CapitalTransfer(furkan, burak, today, 1 * M, null));
        long cashBefore = balance(cash), bankBefore = balance(bank);

        expectError(1055, () -> capitalService.removePartner(furkan));
        UUID shared = vehicle(1 * M, null, List.of(new ShareRequest(furkan, 5000), new ShareRequest(burak, 5000, null, ContributionSource.CAPITAL, null)));
        expectError(1055, () -> capitalService.removePartner(burak));
        vehicleService.remove(shared);

        capitalService.removePartner(burak);
        assertThat(balance(bank)).as("bankaya girdigi 2M bankadan").isEqualTo(bankBefore - 2 * M);
        assertThat(balance(cash)).as("kasaya girdigi 4M + aktarilan 1M kasadan").isEqualTo(cashBefore - 5 * M);
        assertThat(capitalService.partners()).extracting(CapitalResponses.Partner::userId).containsExactly(furkan);
        assertThat(holders()).noneMatch(h -> burak.equals(h.userId()));
        assertThat(holder(furkan).capitalCents()).isEqualTo(49 * M);
        expectError(1035, () -> move(burak, cash, CapitalRequests.Direction.OUT, 1));
        check("kaldirildi");

        // Yeniden ortak yapilabilir
        move(burak, cash, CapitalRequests.Direction.IN, 1 * M);
        assertThat(holder(burak).capitalCents()).isEqualTo(1 * M);
        assertThat(holders().stream().mapToLong(CapitalResponses.Holder::capitalCents).sum()).isEqualTo(netWorthService.summary().totalCents());
        check("yeniden");
    }

    // ------------------------------------------------------------------ yardimcilar

    private UUID person(String name) {
        return contactService.create(new ContactRequest(name, null, ContactType.PERSON, null)).id();
    }

    private void move(UUID contact, UUID account, CapitalRequests.Direction direction, long cents) {
        capitalService.move(contact, new CapitalRequests.Movement(direction, account, today, cents, null));
    }

    private UUID vehicle(long price, List<InstallmentRequest> plan, List<ShareRequest> shares) {
        return vehicleService.create(new VehicleRequest("34 KD " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE,
                Transmission.MANUAL, 10_000, null, null, null, today, plan == null ? null : veli, null, price, price,
                plan == null ? cash : null, plan, shares == null ? null : Ownership.CO_OWNED, null, null, null, null, shares,
                null, null, null, null, null)).vehicle().id();
    }

    private VehicleStatus status(UUID vehicle) {
        return jdbc.queryForObject("select status from vehicle where id = ?", (rs, n) -> VehicleStatus.valueOf(rs.getString(1)), vehicle);
    }

    private List<CapitalResponses.Holder> holders() {
        return netWorthService.holders();
    }

    private CapitalResponses.Holder holder(UUID contact) {
        return holders().stream().filter(h -> contact.equals(h.userId())).findFirst().orElseThrow();
    }

    private long balance(UUID account) {
        return ledgerService.moneyAccounts().stream().filter(a -> a.id().equals(account)).findFirst().orElseThrow().balanceCents();
    }

    private long contactBalance(UUID contact) {
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
