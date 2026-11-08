package com.galeri.app;

import com.galeri.app.controller.LedgerController;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.IncomeRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Diger kazanc: secilen kasa / bankaya giris, kategoriyle; araca bagliysa aracin karina, degilse donem karina.
@SpringBootTest
class OtherIncomeIT {

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerController ledgerController;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private ContactService contactService;
    @Autowired private VehicleService vehicleService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, owner, investor, cash, bank;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        owner = UUID.randomUUID();
        investor = UUID.randomUUID();
        String s = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Kazanc')", tenant, "kazanc-" + s);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Sahip', 'ADMIN')",
                owner, tenant, "91" + s);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Yatirimci', 'INVESTOR')",
                investor, tenant, "92" + s);
        TestSession.open(owner, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        bank = ledgerService.addBank(new com.galeri.app.model.request.AccountRequest("Ziraat")).id();
        capitalService.move(owner, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void genelKazanc_bankayaGirer_donemKarinaKategorisiyleEklenir_duzeltilinceDuser() {
        var kira = ledgerService.addIncome(new IncomeRequest(today, bank, 15_000 * TL, null, null, "Kira geliri", null, "Otopark kirası"));
        ledgerService.addIncome(new IncomeRequest(today, cash, 5_000 * TL, null, null, "Sigorta komisyonu", null, null));
        ledgerService.addIncome(new IncomeRequest(today.plusMonths(1), cash, 9_000 * TL, null, null, "Kira geliri", null, null));

        assertThat(balance(bank)).isEqualTo(15_000 * TL);
        assertThat(kira.type().name()).isEqualTo("OTHER_INCOME");
        assertThat(kira.category()).isEqualTo("Kira geliri");
        CapitalResponses.PeriodProfit p = capitalService.periodProfit(today.withDayOfMonth(1), today.withDayOfMonth(31));
        assertThat(p.otherIncomeCents()).isEqualTo(20_000 * TL);
        assertThat(p.netCents()).isEqualTo(20_000 * TL);
        assertThat(p.otherIncomes()).extracting(CapitalResponses.IncomeLine::category).containsExactly("Kira geliri", "Sigorta komisyonu");
        assertThat(ledgerService.categories(ExpenseRoot.INCOME)).extracting(c -> c.name()).contains("Kira geliri", "Sigorta komisyonu");

        ledgerService.correct(kira.id());
        p = capitalService.periodProfit(today.withDayOfMonth(1), today.withDayOfMonth(31));
        assertThat(p.otherIncomeCents()).isEqualTo(5_000 * TL);
        assertThat(balance(bank)).isZero();
    }

    @Test
    void aracaBagliKazanc_aracinKarinaEklenir_digerKazancaGirmez() {
        UUID car = vehicle();
        ledgerService.addIncome(new IncomeRequest(today, cash, 4_000 * TL, car, null, "Kredi komisyonu", null, null));
        jdbc.update("update vehicle set status = 'SOLD', sale_date = ?, sale_price_cents = ? where id = ?", today, 560_000 * TL, car);

        CapitalResponses.PeriodProfit p = capitalService.periodProfit(today.withDayOfMonth(1), today.withDayOfMonth(31));

        assertThat(p.otherIncomeCents()).isZero();
        assertThat(p.vehicles()).singleElement().satisfies(v -> assertThat(v.profitCents()).isEqualTo(64_000 * TL));
        assertThat(p.vehicleProfitCents()).isEqualTo(64_000 * TL);
        var cost = ledgerService.vehicleCost(car);
        assertThat(cost.incomeCents()).isEqualTo(4_000 * TL);
        assertThat(cost.profitCents()).isEqualTo(64_000 * TL);
    }

    @Test
    void sermayedar_kazancGiremez() {
        TestSession.close();
        TestSession.open(investor, tenant, "INVESTOR");

        assertThatThrownBy(() -> ledgerController.addIncome(new IncomeRequest(today, cash, 1_000 * TL, null, null, "Komisyon", null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private UUID vehicle() {
        UUID seller = contactService.create(new ContactRequest("Satıcı", null, ContactType.PERSON, null)).id();
        String plate = "06 K " + ThreadLocalRandom.current().nextInt(1000, 9999);
        return vehicleService.create(new VehicleRequest(plate, "Fiat", "Egea", (short) 2021, Fuel.GASOLINE, Transmission.MANUAL, 40_000,
                null, null, null, today, seller, null, 500_000 * TL, 500_000 * TL, cash, null, Ownership.DEALER,
                null, null, null, null, null, null, null, null, null, null)).vehicle().id();
    }

    private long balance(UUID account) {
        Long b = jdbc.queryForObject("""
                select coalesce((select sum(amount_cents) from ledger_entry where target_account_id = ?), 0)
                     - coalesce((select sum(amount_cents) from ledger_entry where source_account_id = ?), 0)
                """, Long.class, account, account);
        return b == null ? 0 : b;
    }
}
