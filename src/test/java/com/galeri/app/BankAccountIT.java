package com.galeri.app;

import com.galeri.app.exception.runtime.BankAccountNotRemovableException;
import com.galeri.app.exception.runtime.ClosedAccountException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.NetWorthService;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Banka hesabini duzenleme ve kaldirma gercek Postgres'te: hic hareketi yoksa silinir, varsa bakiyesi 0 iken arsivlenir.
@SpringBootTest
class BankAccountIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock bankAccountClock() {
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
    private UUID tenant, admin, cash;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Banka')", tenant, "bnk-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Furkan', 'ADMIN')",
                admin, tenant, "96" + suffix);
        TestSession.open(admin, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        capitalService.move(admin, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void adiDegisir_hareketleriAyniHesaptaKalir() {
        UUID bank = ledgerService.addBank(new AccountRequest("Ziraat")).id();
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 100_000 * TL, null));

        AccountResponse renamed = ledgerService.renameBank(bank, new AccountRequest("  Ziraat Ticari "));

        assertThat(renamed.name()).isEqualTo("Ziraat Ticari");
        assertThat(renamed.balanceCents()).isEqualTo(100_000 * TL);
        assertThat(ledgerService.entries(bank)).allMatch(e -> e.target().id().equals(bank) ? e.target().name().equals("Ziraat Ticari")
                : e.source().name().equals("Ziraat Ticari"));
    }

    @Test
    void hicKullanilmamisHesap_tamamenSilinir() {
        UUID bank = ledgerService.addBank(new AccountRequest("Yanlış banka")).id();

        ledgerService.removeBank(bank);

        assertThat(ledgerService.moneyAccounts()).noneMatch(a -> a.id().equals(bank));
        assertThat(jdbc.queryForObject("select count(*) from account where id = ?", Integer.class, bank)).isZero();
    }

    @Test
    void parasiOlanHesapKaldirilamaz_paraAktarilincaArsivlenir_hareketleriKalir() {
        UUID bank = ledgerService.addBank(new AccountRequest("Halkbank")).id();
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 200_000 * TL, null));

        assertThatThrownBy(() -> ledgerService.removeBank(bank)).isInstanceOf(BankAccountNotRemovableException.class);

        ledgerService.transfer(new CapitalRequests.Transfer(bank, cash, today, 200_000 * TL, null));
        ledgerService.removeBank(bank);

        assertThat(ledgerService.moneyAccounts()).noneMatch(a -> a.id().equals(bank));
        assertThat(netWorthService.holdings().accounts()).noneMatch(h -> h.accountId().equals(bank));
        assertThat(ledgerService.entries(null)).as("gecmis hareketler duruyor").anyMatch(e -> e.target().id().equals(bank));
        assertThatThrownBy(() -> ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 1 * TL, null)))
                .as("kaldirilan hesaba islem yazilmaz").isInstanceOf(ClosedAccountException.class);
        assertThatThrownBy(() -> ledgerService.renameBank(bank, new AccountRequest("X"))).isInstanceOf(InvalidAccountException.class);
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, "arsivlendi");
        assertThat(netWorthService.summary().differenceCents()).isZero();
    }

    @Test
    void arsivlenenHesabaDuzeltmeyleParaGelirse_gorunur_paraCikarilabilir_bitinceYineGizlenir() {
        UUID bank = ledgerService.addBank(new AccountRequest("Babamın Vakıfbank")).id();
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 20_000 * TL, null));
        var drain = ledgerService.transfer(new CapitalRequests.Transfer(bank, cash, today, 20_000 * TL, null));
        ledgerService.removeBank(bank);

        ledgerService.correct(drain.id());   // bosaltan transfer duzeltildi: para arsivli hesaba geri dondu
        assertThat(ledgerService.moneyAccounts()).as("parasi olunca gorunur")
                .anyMatch(a -> a.id().equals(bank) && a.balanceCents() == 20_000 * TL);

        ledgerService.transfer(new CapitalRequests.Transfer(bank, cash, today, 20_000 * TL, null));
        assertThat(ledgerService.moneyAccounts()).as("para bitince yine gizli").noneMatch(a -> a.id().equals(bank));
        assertThatThrownBy(() -> ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 1 * TL, null)))
                .isInstanceOf(ClosedAccountException.class);
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, "arsivli hesap bosaltildi");
        assertThat(netWorthService.summary().differenceCents()).isZero();
    }

    @Test
    void kasaDuzenlenmezVeKaldirilamaz() {
        assertThatThrownBy(() -> ledgerService.renameBank(cash, new AccountRequest("Kasa 2"))).isInstanceOf(InvalidAccountException.class);
        assertThatThrownBy(() -> ledgerService.removeBank(cash)).isInstanceOf(InvalidAccountException.class);
    }
}
