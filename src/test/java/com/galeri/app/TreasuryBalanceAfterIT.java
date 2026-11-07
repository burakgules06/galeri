package com.galeri.app;

import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

// Kasa son hareketlerinde her hareketin yaninda islem sonrasi kasa / banka bakiyesi (yazildigi anin fotografi).
@SpringBootTest
class TreasuryBalanceAfterIT {

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID owner, cash, bank;

    @BeforeEach
    void setUp() {
        UUID tenant = UUID.randomUUID();
        owner = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Bakiye')", tenant, "bakiye-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Sahip', 'ADMIN')",
                owner, tenant, "94" + suffix);
        TestSession.open(owner, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        bank = ledgerService.addBank(new AccountRequest("Ziraat")).id();
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void herHareket_yazildigiAndakiKasaBankaBakiyesiniTasir_geriTarihliKayitEskiFotograflariDegistirmez() {
        UUID capitalIn = capitalService.move(owner, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 50_000 * TL, null)).id();
        UUID fuel = ledgerService.addExpense(new ExpenseRequest(today, cash, 1_000 * TL, null, null, "Yakıt", null, null, null)).id();
        UUID transfer = ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, today, 20_000 * TL, null)).id();
        // Gecmis tarihli masraf: kendi fotografi o anki kasa; ondan once yazilanlarin fotografi degismez
        UUID backdated = ledgerService.addExpense(new ExpenseRequest(today.minusDays(5), cash, 500 * TL, null, null, "Yakıt", null, null, null)).id();

        List<LedgerEntryResponse> list = ledgerService.entries(null);

        assertThat(entry(list, capitalIn).targetBalanceAfterCents()).isEqualTo(50_000 * TL);
        LedgerEntryResponse f = entry(list, fuel);
        assertThat(f.sourceBalanceAfterCents()).isEqualTo(49_000 * TL);
        assertThat(f.targetBalanceAfterCents()).as("gider hesabi kasa / banka degil").isNull();
        LedgerEntryResponse t = entry(list, transfer);
        assertThat(t.sourceBalanceAfterCents()).isEqualTo(29_000 * TL);
        assertThat(t.targetBalanceAfterCents()).isEqualTo(20_000 * TL);
        assertThat(entry(list, backdated).sourceBalanceAfterCents()).isEqualTo(28_500 * TL);

        // Tek hesap filtresiyle de ayni degerler
        assertThat(entry(ledgerService.entries(bank), transfer).targetBalanceAfterCents()).isEqualTo(20_000 * TL);
    }

    private static LedgerEntryResponse entry(List<LedgerEntryResponse> list, UUID id) {
        return list.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }
}
