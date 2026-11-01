package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CapitalBase;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.ContributionSource;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
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

// Dukkan sahibi modeli (V16) gercek Postgres'te: havuz = kasa + bankalar + araclar + alacaklar - borclar; sermayedarin
// sermayesi defterden, dukkan sahibininki havuzdan kalan. Giderler ve cekisler sadece dukkan sahibini kucultur; kar sadece
// arac satisinda aracin kar yuzdesiyle. Kisiler arasi aktarim kasayi degistirmez.
@SpringBootTest
class HoldersIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock holdersClock() {
            return Clock.fixed(Instant.parse("2026-08-01T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long M = 1_000_000L * 100;   // 1 milyon TL kurus
    private static final long K = 1_000L * 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private VehicleService vehicleService;
    @Autowired private NetWorthService netWorthService;

    private final LocalDate today = LocalDate.of(2026, 8, 1);
    private UUID tenant, cash, furkan, burak, selin, veli;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Havuz')", tenant, "havuz-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                user, tenant, "97" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        furkan = user;   // dukkan sahibi kullanici
        burak = TestUsers.investor(jdbc, tenant, "Burak Şahin");
        selin = TestUsers.investor(jdbc, tenant, "Selin");
        veli = person("Veli");
        move(furkan, CapitalRequests.Direction.IN, 50 * M);
        move(burak, CapitalRequests.Direction.IN, 6 * M);
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void havuz_dukkanSahibiKalan_sermayedarDefterden_giderSadeceSahibi_karAracYuzdesiyle_aktarimKasayiDegistirmez() {
        expect("baslangic", furkan, 50 * M, 50 * M, 0);
        expect("baslangic", burak, 6 * M, 6 * M, 0);

        UUID dealer = vehicle(20 * M, null);   // kasayla alinan galeri araci: dukkan sahibinin mali
        expect("galeri araci", furkan, 50 * M, 30 * M, 20 * M);
        expect("galeri araci", burak, 6 * M, 6 * M, 0);

        // Ortak arac 5M: Havuz %20, Burak %80 sermayeden (kar payi da %80)
        UUID shared = vehicle(5 * M, List.of(new ShareRequest(furkan, 2000), new ShareRequest(burak, 8000, null, ContributionSource.CAPITAL, null)));
        expect("ortak arac", burak, 6 * M, 2 * M, 4 * M);
        expect("ortak arac", furkan, 50 * M, 29 * M, 21 * M);
        assertThat(holder(burak).vehicles()).singleElement().satisfies(v -> {
            assertThat(v.vehicle().id()).isEqualTo(shared);
            assertThat(v.cents()).isEqualTo(4 * M);
        });
        assertThat(holder(furkan).vehicles()).extracting(v -> v.vehicle().id()).containsExactly(dealer, shared);
        check("ortak arac");

        // Gider sadece dukkan sahibinden duser
        ledgerService.addExpense(new ExpenseRequest(today, cash, 100 * K, null, null, "Kira", null, null, null));
        expect("gider", furkan, 49_900 * K, 28_900 * K, 21 * M);
        expect("gider", burak, 6 * M, 2 * M, 4 * M);

        // Aktarim: kasa ayni, sahiplik degisir
        capitalService.transferCapital(new CapitalRequests.CapitalTransfer(furkan, burak, today, 1 * M, null));
        expect("aktarim", burak, 7 * M, 3 * M, 4 * M);
        expect("aktarim", furkan, 48_900 * K, 27_900 * K, 21 * M);
        assertThat(cashBalance()).isEqualTo(30_900 * K);
        expectError(1036, () -> capitalService.transferCapital(new CapitalRequests.CapitalTransfer(burak, selin, today, 3 * M + 1, null)));
        check("aktarim");

        // Yuzde sadece bilgi: tabana gore
        capitalService.setBase(burak, new CapitalRequests.Base(CapitalBase.CASH));
        assertThat(holder(burak).shareBp()).as("nakit tabani: 3M / 30,9M").isEqualTo(971);
        capitalService.setBase(burak, new CapitalRequests.Base(CapitalBase.ALL));
        assertThat(holder(burak).shareBp()).as("tum varlik: 7M / 55,9M").isEqualTo(1252);

        // Ortak arac 6M'e satildi: kar 1M, Burak'a %80
        vehicleService.sell(shared, new VehicleSaleRequest(today, veli, null, 6 * M,
                List.of(new InstallmentRequest(today, 6 * M, PaymentMethod.CASH, null, null, true, cash))));
        expect("satis", burak, 7_800 * K, 7_800 * K, 0);
        expect("satis", furkan, 49_100 * K, 29_100 * K, 20 * M);
        check("satis");

        // Dukkan sahibinin cekisi sinirlanmaz (kasadaki payi bitse de); sermayedarin parasina dokunmaz
        move(furkan, CapitalRequests.Direction.OUT, 30 * M);
        expect("sahip cekti", furkan, 19_100 * K, -900 * K, 20 * M);
        expect("sahip cekti", burak, 7_800 * K, 7_800 * K, 0);
        assertThat(holders().stream().mapToLong(CapitalResponses.Holder::capitalCents).sum())
                .as("herkesin sermayesi = havuz").isEqualTo(netWorthService.summary().totalCents());
        check("sahip cekti");
    }

    @Test
    void kasaVeBanka_kisilereDagilir_transferOranindaTasinir_ortakAracKatkisiHesaptanDuser() {
        UUID bank = ledgerService.addBank(new com.galeri.app.model.request.AccountRequest("Ziraat")).id();
        capitalService.move(selin, new CapitalRequests.Movement(CapitalRequests.Direction.IN, bank, today, 4 * M, null));
        // Kasa: Furkan 50 + Burak 6; Banka: Selin 4
        expectHolding("giris", cash, burak, 6 * M);
        expectHolding("giris", bank, selin, 4 * M);
        expectHolding("giris", cash, null, 50 * M);

        // Kasadan bankaya 28 milyon (kasanin yarisi): herkesin kasadaki parasinin yarisi tasinir
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 28 * M, null));
        expectHolding("transfer", cash, burak, 3 * M);
        expectHolding("transfer", bank, burak, 3 * M);
        expectHolding("transfer", bank, selin, 4 * M);
        expectHolding("transfer", cash, null, 25 * M);

        // Ortakli arac kasadan 4 milyon, Burak %50: Burak'in kasadaki parasindan 2 milyon araca
        vehicle(4 * M, List.of(new ShareRequest(furkan, 5000), new ShareRequest(burak, 5000, null, ContributionSource.CAPITAL, null)));
        expectHolding("ortak arac", cash, burak, 1 * M);
        expectHolding("ortak arac", cash, null, 23 * M);
        check("dagilim");
    }

    private void expectHolding(String step, UUID account, UUID user, long cents) {
        var holdings = netWorthService.holdings();
        assertThat(holdings).allSatisfy(h -> assertThat(h.parts().stream().mapToLong(p -> p.cents()).sum())
                .as("%s: %s parcalar toplami", step, h.name()).isEqualTo(h.balanceCents()));
        var acc = holdings.stream().filter(h -> h.accountId().equals(account)).findFirst().orElseThrow();
        long actual = acc.parts().stream().filter(p -> user == null ? p.owner() : user.equals(p.userId()))
                .mapToLong(p -> p.cents()).sum();
        assertThat(actual).as("%s: %s / %s", step, acc.name(), user == null ? "dukkan sahibi" : user).isEqualTo(cents);
    }

    // ------------------------------------------------------------------ yardimcilar

    private UUID person(String name) {
        return contactService.create(new ContactRequest(name, null, ContactType.PERSON, null)).id();
    }

    private void move(UUID contact, CapitalRequests.Direction direction, long cents) {
        capitalService.move(contact, new CapitalRequests.Movement(direction, cash, today, cents, null));
    }

    private UUID vehicle(long price, List<ShareRequest> shares) {
        return vehicleService.create(new VehicleRequest("34 HV " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE,
                Transmission.MANUAL, 10_000, null, null, null, today, null, null, price, price, cash, null,
                shares == null ? null : Ownership.CO_OWNED, null, null, null, null, shares, null, null, null, null, null)).vehicle().id();
    }

    private List<CapitalResponses.Holder> holders() {
        return netWorthService.holders();
    }

    private CapitalResponses.Holder holder(UUID contact) {
        return holders().stream().filter(h -> contact.equals(h.userId())).findFirst().orElseThrow();
    }

    private void expect(String step, UUID contact, long capital, long cashPart, long inVehicles) {
        CapitalResponses.Holder h = holder(contact);
        assertThat(h.capitalCents()).as("%s: sermaye", step).isEqualTo(capital);
        assertThat(h.cashCents()).as("%s: nakit", step).isEqualTo(cashPart);
        assertThat(h.vehicleCents()).as("%s: aracta", step).isEqualTo(inVehicles);
        assertThat(h.cashCents() + h.vehicleCents() + h.otherCents()).as("%s: parcalar toplami", step).isEqualTo(capital);
    }

    private long cashBalance() {
        return ledgerService.moneyAccounts().stream().filter(a -> a.id().equals(cash)).findFirst().orElseThrow().balanceCents();
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
