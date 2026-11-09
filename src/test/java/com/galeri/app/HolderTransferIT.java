package com.galeri.app;

import com.galeri.app.exception.runtime.HolderTransferExceededException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.response.AccountHoldings;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.LedgerService;
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

// Sermayedarin parasinin kasa ve banka arasinda tasinmasi (V32) gercek Postgres'te.
// Furkan dukkan sahibi (kasada 2.000.000), Burak sermayedar (kasada 1.000.000), bir banka hesabi.
@SpringBootTest
class HolderTransferIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock holderTransferClock() {
            return Clock.fixed(Instant.parse("2026-05-10T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private NetWorthService netWorthService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, admin, burak, cash, bank;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Virman')", tenant, "vir-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                admin, tenant, "95" + suffix);
        asAdmin();
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        bank = ledgerService.addBank(new AccountRequest("Ziraat")).id();
        burak = TestUsers.investor(jdbc, tenant, "Burak");
        capitalService.move(admin, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 2_000_000 * TL, null));
        capitalService.move(burak, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void dukkanSahibi_sermayedarinParasiniBankayaTasir_payiHedefHesabaGecer_duzeltilinceGeriDoner() {
        var entry = ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 400_000 * TL, null, burak));

        assertThat(burakIn(cash)).isEqualTo(600_000 * TL);
        assertThat(burakIn(bank)).isEqualTo(400_000 * TL);
        assertThat(ownerIn(bank)).as("dukkan sahibinin bankadaki parasi degismez").isZero();
        check("tasindi");

        ledgerService.correct(entry.id());
        assertThat(burakIn(cash)).isEqualTo(1_000_000 * TL);
        assertThat(burakIn(bank)).isZero();
        check("duzeltildi");
    }

    @Test
    void sahipBelirtilmezse_eskisiGibi_dukkanSahibininParasiTasinir() {
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 500_000 * TL, null));
        assertThat(burakIn(cash)).isEqualTo(1_000_000 * TL);
        assertThat(ownerIn(bank)).isEqualTo(500_000 * TL);
        check("dukkan sahibi");
    }

    @Test
    void hesaptakiParasindanFazlasiTasinamaz() {
        assertThatThrownBy(() -> ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 1_100_000 * TL, null, burak)))
                .isInstanceOf(HolderTransferExceededException.class);
        assertThatThrownBy(() -> ledgerService.transfer(new CapitalRequests.Transfer(bank, cash, today, 1 * TL, null, burak)))
                .isInstanceOf(HolderTransferExceededException.class);
    }

    @Test
    void sermayedar_sadeceKendiParasiniTasir() {
        asInvestor();
        // Baskasini (dukkan sahibini) secse bile kendi parasi tasinir
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 300_000 * TL, null, admin));
        asAdmin();
        assertThat(burakIn(cash)).isEqualTo(700_000 * TL);
        assertThat(burakIn(bank)).isEqualTo(300_000 * TL);
        check("sermayedar tasidi");

        asInvestor();
        assertThat(ledgerService.entries(null)).as("transfer sermayedarin hareketlerinde").anyMatch(e -> e.amountCents() == 300_000 * TL
                && e.source().id().equals(cash) && e.target().id().equals(bank));
        assertThatThrownBy(() -> ledgerService.transfer(new CapitalRequests.Transfer(bank, cash, today, 400_000 * TL, null, null)))
                .isInstanceOf(HolderTransferExceededException.class);
    }

    private long burakIn(UUID account) {
        return part(account, false);
    }

    private long ownerIn(UUID account) {
        return part(account, true);
    }

    private long part(UUID account, boolean owner) {
        AccountHoldings h = netWorthService.holdings().accounts().stream().filter(a -> a.accountId().equals(account)).findFirst().orElseThrow();
        return h.parts().stream().filter(p -> p.owner() == owner && (owner || burak.equals(p.userId())))
                .mapToLong(AccountHoldings.Part::cents).sum();
    }

    private void asAdmin() {
        TestSession.close();
        TestSession.open(admin, tenant, "ADMIN");
    }

    private void asInvestor() {
        TestSession.close();
        TestSession.open(burak, tenant, "INVESTOR");
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
        assertThat(netWorthService.summary().differenceCents()).as("%s: denge", step).isZero();
    }
}
