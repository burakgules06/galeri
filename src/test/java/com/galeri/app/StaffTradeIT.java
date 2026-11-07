package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
import com.galeri.app.support.TestSession;
import org.assertj.core.api.ThrowableAssert;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Yetkili calisan (can_trade) gercek Postgres'te: pesin alim ve satim girer, alan / satan kendisi yazilir;
// vadeli, konsinye, ortakli ve odenmemis satirli islemler reddedilir. Her adimdan sonra LedgerInvariants.
@SpringBootTest
class StaffTradeIT {

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private com.galeri.app.service.CoOwnershipService coOwnershipService;
    @Autowired private VehicleService vehicleService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, owner, staff, cash, bank, seller, buyer;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        owner = UUID.randomUUID();
        staff = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Calisan')", tenant, "calisan-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Sahip', 'ADMIN')",
                owner, tenant, "93" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role, can_trade) values (?, ?, ?, 'x', 'Emre', 'STAFF', true)",
                staff, tenant, "92" + suffix);
        TestSession.open(owner, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        bank = ledgerService.addBank(new com.galeri.app.model.request.AccountRequest("Vakıfbank")).id();
        capitalService.move(owner, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 5_000_000 * TL, null));
        seller = contactService.create(new ContactRequest("Satıcı Ali", null, ContactType.PERSON, null)).id();
        buyer = contactService.create(new ContactRequest("Alıcı Veli", null, ContactType.PERSON, null)).id();
        TestSession.close();
        TestSession.open(staff, tenant, "STAFF");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void yetkiliCalisan_pesinAlipSatar_alanVeSatanKendisiYazilir() {
        VehicleDetailAdmin bought = vehicleService.create(request(800_000 * TL, cash, Ownership.DEALER, null, null));
        UUID id = bought.vehicle().id();
        assertThat(bought.boughtBy().id()).isEqualTo(staff);
        assertThat(money(cash)).isEqualTo(4_200_000 * TL);
        check("calisan pesin alim");

        // Kasa + banka karisik pesin satis
        VehicleDetailAdmin sold = vehicleService.sell(id, new VehicleSaleRequest(today, buyer, null, 950_000 * TL, List.of(
                paid(500_000 * TL, cash), paid(450_000 * TL, bank))));
        assertThat(sold.soldBy().id()).isEqualTo(staff);
        assertThat(money(cash)).isEqualTo(4_700_000 * TL);
        assertThat(money(bank)).isEqualTo(450_000 * TL);
        check("calisan pesin satis");
    }

    @Test
    void calisan_vadeliKonsinyeOrtakliVeOdenmemisSatirliIslem_reddedilir() {
        expectError(1064, () -> vehicleService.create(request(800_000 * TL, null, Ownership.DEALER, null,
                List.of(new InstallmentRequest(today.plusDays(30), 800_000 * TL, PaymentMethod.PROMISSORY_NOTE, "S-1", null, false, null)))));
        expectError(1064, () -> vehicleService.create(request(800_000 * TL, cash, Ownership.CONSIGNMENT, null, null)));
        expectError(1064, () -> vehicleService.create(request(800_000 * TL, null, Ownership.DEALER, null, null)));   // hesap yok
        expectError(1064, () -> vehicleService.create(request(800_000 * TL, UUID.randomUUID(), Ownership.DEALER, null, null)));

        UUID id = vehicleService.create(request(800_000 * TL, cash, Ownership.DEALER, null, null)).vehicle().id();
        expectError(1064, () -> vehicleService.sell(id, new VehicleSaleRequest(today, buyer, null, 900_000 * TL, List.of(
                new InstallmentRequest(today.plusDays(30), 900_000 * TL, PaymentMethod.PROMISSORY_NOTE, "S-2", null, false, null)))));
        assertThat(money(cash)).as("reddedilenler kasaya dokunmadi").isEqualTo(4_200_000 * TL);
        check("calisan kurallari");
    }

    private InstallmentRequest paid(long cents, UUID account) {
        return new InstallmentRequest(today, cents, PaymentMethod.CASH, null, null, true, account);
    }

    private VehicleRequest request(long price, UUID account, Ownership ownership, UUID consignor, List<InstallmentRequest> plan) {
        return new VehicleRequest("34 ST " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, seller, null, price, price, account, plan, ownership, consignor, null, null, null,
                null, null, null, null, null, null);
    }

    private long money(UUID account) {
        Long b = jdbc.queryForObject("""
                select coalesce((select sum(amount_cents) from ledger_entry where target_account_id = ?), 0)
                     - coalesce((select sum(amount_cents) from ledger_entry where source_account_id = ?), 0)
                """, Long.class, account, account);
        return b == null ? 0 : b;
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService, coOwnershipService).check(tenant, step);
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
