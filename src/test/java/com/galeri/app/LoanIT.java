package com.galeri.app;

import com.galeri.app.exception.runtime.LoanExceededException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.LoanRequest;
import com.galeri.app.model.response.AccountHoldings;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.LoanBalance;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.LoanService;
import com.galeri.app.service.NetWorthService;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Kisiler arasi borc (V33) gercek Postgres'te. Furkan dukkan sahibi (kasada 2.000.000), Burak sermayedar (kasada 300.000),
// Yusuf sermayedar (bankada 50.000).
@SpringBootTest
class LoanIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock loanClock() {
            return Clock.fixed(Instant.parse("2026-05-10T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private LoanService loanService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private NetWorthService netWorthService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, furkan, burak, yusuf, cash, bank;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        furkan = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Borc')", tenant, "brc-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                furkan, tenant, "97" + suffix);
        asAdmin();
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        bank = ledgerService.addBank(new AccountRequest("Vakıfbank")).id();
        burak = TestUsers.investor(jdbc, tenant, "Burak");
        yusuf = TestUsers.investor(jdbc, tenant, "Yusuf");
        capitalService.move(furkan, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 2_000_000 * TL, null));
        capitalService.move(burak, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 300_000 * TL, null));
        capitalService.move(yusuf, new CapitalRequests.Movement(CapitalRequests.Direction.IN, bank, today, 50_000 * TL, null));
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void dukkanSahibiBorcVerir_kasaDegismez_sermayelerVeHesaptakiPaylarElDegistirir() {
        long cashBefore = cashBalance();

        loanService.lend(new LoanRequest(furkan, burak, cash, today, 100_000 * TL, null));

        assertThat(cashBalance()).as("para kasada kalir").isEqualTo(cashBefore);
        assertThat(capital(burak)).isEqualTo(400_000 * TL);
        assertThat(capital(furkan)).isEqualTo(1_900_000 * TL);
        assertThat(part(cash, burak)).as("Burak'in kasadaki parasi").isEqualTo(400_000 * TL);
        assertThat(ownerPart(cash)).isEqualTo(1_900_000 * TL);
        assertThat(netWorthService.holdings().unassigned()).as("hesabi belli olmayan para kalmaz").isEmpty();
        assertThat(loanService.balances()).singleElement().satisfies(b -> {
            assertThat(b.borrowerUserId()).isEqualTo(burak);
            assertThat(b.lenderUserId()).isEqualTo(furkan);
            assertThat(b.outstandingCents()).isEqualTo(100_000 * TL);
        });
        check("borc verildi");
    }

    @Test
    void kismiVeTamOdeme_kalanBorcuAsamaz() {
        loanService.lend(new LoanRequest(furkan, burak, cash, today, 100_000 * TL, null));

        loanService.repay(new LoanRequest(furkan, burak, cash, today, 40_000 * TL, null));
        assertThat(outstanding(burak, furkan)).isEqualTo(60_000 * TL);
        assertThat(capital(burak)).isEqualTo(360_000 * TL);
        check("kismi odeme");

        assertThatThrownBy(() -> loanService.repay(new LoanRequest(furkan, burak, cash, today, 70_000 * TL, null)))
                .isInstanceOf(LoanExceededException.class);

        loanService.repay(new LoanRequest(furkan, burak, cash, today, 60_000 * TL, null));
        assertThat(loanService.balances()).isEmpty();
        assertThat(capital(burak)).isEqualTo(300_000 * TL);
        assertThat(capital(furkan)).isEqualTo(2_000_000 * TL);
        check("borc kapandi");
    }

    @Test
    void sermayedarSadeceOHesaptakiParasindanBorcVerir() {
        assertThatThrownBy(() -> loanService.lend(new LoanRequest(yusuf, burak, bank, today, 80_000 * TL, null)))
                .as("bankada 50.000'i var").isInstanceOf(LoanExceededException.class);
        assertThatThrownBy(() -> loanService.lend(new LoanRequest(yusuf, burak, cash, today, 10_000 * TL, null)))
                .as("kasada parasi yok").isInstanceOf(LoanExceededException.class);

        loanService.lend(new LoanRequest(yusuf, burak, bank, today, 30_000 * TL, null));
        assertThat(part(bank, yusuf)).isEqualTo(20_000 * TL);
        assertThat(part(bank, burak)).isEqualTo(30_000 * TL);
        assertThat(capital(yusuf)).isEqualTo(20_000 * TL);
        check("sermayedar borc verdi");

        // Geri odeme Burak'in parasinin oldugu baska bir hesaptan (kasa) yapilabilir: para Yusuf'a kasada doner
        loanService.repay(new LoanRequest(yusuf, burak, cash, today, 30_000 * TL, null));
        assertThat(part(cash, yusuf)).as("Yusuf'un parasi artik kasada").isEqualTo(30_000 * TL);
        check("geri odendi");
    }

    @Test
    void borcDuzeltilinceKalanBorcVePaylarGeriDoner() {
        var entry = loanService.lend(new LoanRequest(furkan, burak, cash, today, 100_000 * TL, null));

        ledgerService.correct(entry.id());

        assertThat(loanService.balances()).isEmpty();
        assertThat(part(cash, burak)).isEqualTo(300_000 * TL);
        assertThat(capital(burak)).isEqualTo(300_000 * TL);
        check("duzeltildi");
    }

    @Test
    void sermayedarSadeceKendiBorclariniGorur() {
        loanService.lend(new LoanRequest(furkan, burak, cash, today, 100_000 * TL, null));
        loanService.lend(new LoanRequest(furkan, yusuf, bank, today, 5_000 * TL, null));

        asInvestor(yusuf);
        assertThat(loanService.balances()).singleElement()
                .satisfies(b -> assertThat(b.borrowerUserId()).isEqualTo(yusuf));
        asAdmin();
        assertThat(loanService.balances()).hasSize(2);
    }

    // ------------------------------------------------------------------ yardimcilar

    private long outstanding(UUID borrower, UUID lender) {
        return loanService.balances().stream().filter(b -> b.borrowerUserId().equals(borrower) && b.lenderUserId().equals(lender))
                .mapToLong(LoanBalance::outstandingCents).sum();
    }

    private long capital(UUID user) {
        return capitalService.partners().stream().filter(p -> user.equals(p.userId())).mapToLong(CapitalResponses.Partner::capitalCents)
                .sum();
    }

    private long cashBalance() {
        return jdbc.queryForObject("""
                select coalesce((select sum(amount_cents) from ledger_entry where target_account_id = ?), 0)
                     - coalesce((select sum(amount_cents) from ledger_entry where source_account_id = ?), 0)""", Long.class, cash, cash);
    }

    private long part(UUID account, UUID user) {
        return holdings(account).parts().stream().filter(p -> !p.owner() && user.equals(p.userId()))
                .mapToLong(AccountHoldings.Part::cents).sum();
    }

    private long ownerPart(UUID account) {
        return holdings(account).parts().stream().filter(AccountHoldings.Part::owner).mapToLong(AccountHoldings.Part::cents).sum();
    }

    private AccountHoldings holdings(UUID account) {
        return netWorthService.holdings().accounts().stream().filter(a -> a.accountId().equals(account)).findFirst().orElseThrow();
    }

    private void asAdmin() {
        TestSession.close();
        TestSession.open(furkan, tenant, "ADMIN");
    }

    private void asInvestor(UUID user) {
        TestSession.close();
        TestSession.open(user, tenant, "INVESTOR");
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
        assertThat(netWorthService.summary().differenceCents()).as("%s: denge", step).isZero();
    }
}
