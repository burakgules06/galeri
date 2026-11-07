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
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
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

// "Arac masrafi girebilir" yetkili calisan (can_add_expense): secilen kasa / bankadan araca masraf girer, cevapta tutar yok;
// genel gider, cepten masraf ve masraf listesi yok. Yetki can_trade'den bagimsiz.
@SpringBootTest
class StaffExpenseIT {

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerController ledgerController;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private ContactService contactService;
    @Autowired private VehicleService vehicleService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, owner, expenseStaff, tradeStaff, cash, vehicle;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        owner = UUID.randomUUID();
        expenseStaff = UUID.randomUUID();
        tradeStaff = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Masraf')", tenant, "masraf-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Sahip', 'ADMIN')",
                owner, tenant, "95" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role, can_add_expense) values (?, ?, ?, 'x', 'Emre', 'STAFF', true)",
                expenseStaff, tenant, "96" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role, can_trade) values (?, ?, ?, 'x', 'Can', 'STAFF', true)",
                tradeStaff, tenant, "97" + suffix);
        TestSession.open(owner, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        capitalService.move(owner, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 1_000_000 * TL, null));
        UUID seller = contactService.create(new ContactRequest("Satıcı Ali", null, ContactType.PERSON, null)).id();
        vehicle = vehicleService.create(new VehicleRequest("34 MS " + suffix.substring(0, 3), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE,
                Transmission.MANUAL, 10_000, null, null, null, today, seller, null, 500_000 * TL, 500_000 * TL, cash, null,
                Ownership.DEALER, null, null, null, null, null, null, null, null, null, null)).vehicle().id();
        TestSession.close();
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void yetkiliCalisan_aracaMasrafGirer_kasadanDuser_cevaptaTutarYok() {
        TestSession.open(expenseStaff, tenant, "STAFF");

        assertThat(ledgerController.moneyAccounts().getData()).allMatch(a -> a.balanceCents() == 0);
        assertThat(ledgerController.categories(ExpenseRoot.VEHICLE).isSuccess()).isTrue();
        var res = ledgerController.addExpense(new ExpenseRequest(today, cash, 2_500 * TL, vehicle, null, "Yıkama", null, "Pasta cila", null));

        assertThat(res.getData()).isNull();
        assertThat(money(cash)).isEqualTo(497_500 * TL);
        Long cost = jdbc.queryForObject("select count(*) from ledger_entry where vehicle_id = ? and type = 'VEHICLE_EXPENSE' and created_by_id = ?",
                Long.class, vehicle, expenseStaff);
        assertThat(cost).isEqualTo(1);
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, "calisan masrafi");
    }

    @Test
    void yetkiliCalisan_genelGiderVeCeptenMasrafGiremez_listeyiGoremez() {
        TestSession.open(expenseStaff, tenant, "STAFF");

        assertThatThrownBy(() -> ledgerController.addExpense(new ExpenseRequest(today, cash, 1_000 * TL, null, null, "Kira", null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ledgerController.addExpense(new ExpenseRequest(today, null, 1_000 * TL, vehicle, null, "Yıkama", null, null, null, true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ledgerController.expenses(null, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(money(cash)).isEqualTo(500_000 * TL);
    }

    @Test
    void sadeceAlimSatimYetkisiOlanCalisan_masrafGiremez() {
        TestSession.open(tradeStaff, tenant, "STAFF");

        assertThatThrownBy(() -> ledgerController.addExpense(new ExpenseRequest(today, cash, 1_000 * TL, vehicle, null, "Yıkama", null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ledgerController.categories(ExpenseRoot.VEHICLE)).isInstanceOf(AccessDeniedException.class);
    }

    private long money(UUID account) {
        Long b = jdbc.queryForObject("""
                select coalesce((select sum(amount_cents) from ledger_entry where target_account_id = ?), 0)
                     - coalesce((select sum(amount_cents) from ledger_entry where source_account_id = ?), 0)
                """, Long.class, account, account);
        return b == null ? 0 : b;
    }
}
