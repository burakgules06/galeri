package com.galeri.app;

import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.EndorseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
import com.galeri.app.support.TestSession;
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

// Cek / senet takibi gercek Postgres'te: cekle satis, ciro (alis senedine ya da cariye), karsiliksiz, kismi tahsilat.
// Her test bos bir galeride; Furkan'in 10.000.000 sermayesi kasada. Her adimdan sonra LedgerInvariants.
@SpringBootTest
class ChequeIT {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock chequeClock() {
            return Clock.fixed(Instant.parse("2026-05-10T07:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private VehicleService vehicleService;
    @Autowired private PaymentPlanService paymentPlanService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, cash, ali, mehmet, veli;
    private int plates;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Cek')", tenant, "cek-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "94" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        UUID furkan = person("Furkan");
        ali = person("Ali");
        mehmet = person("Mehmet");
        veli = person("Veli");
        capitalService.move(furkan, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 10_000_000 * TL, null));
        check("hazirlik");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void cekleSatis_cekBilgisiTutulur_vadedeKasayaTahsilEdilir() {
        UUID v = cashVehicle(800_000 * TL);
        sell(v, ali, List.of(
                new InstallmentRequest(today, 300_000 * TL, PaymentMethod.CASH, null, null, true, cash),
                cheque(today.plusDays(30), 700_000 * TL, "123456")));
        InstallmentResponse c = open(v).getFirst();
        assertThat(c.method()).isEqualTo(PaymentMethod.CHEQUE);
        assertThat(c.noteNo()).isEqualTo("123456");
        assertThat(c.bankName()).isEqualTo("Ziraat");
        assertThat(c.drawerName()).isEqualTo("Ayşe Kaya");
        assertThat(balance(ali)).isEqualTo(700_000 * TL);
        check("satis");

        paymentPlanService.collect(c.id(), new CollectRequest(cash, today.plusDays(30)));
        assertThat(balance(ali)).isZero();
        assertThat(cashBalance()).isEqualTo((10_000_000 - 800_000 + 1_000_000) * TL);
        check("tahsil");
    }

    @Test
    void ciro_alisSenedineVerilir_senetCektenBuyukseBolunur_geriAlininca_ikisiDeAcilir() {
        UUID bought = plannedVehicle(800_000 * TL, mehmet);   // Mehmet'e 800.000 senet borcumuz var
        UUID sold = cashVehicle(400_000 * TL);
        sell(sold, ali, List.of(cheque(today.plusDays(45), 500_000 * TL, "777")));
        long cashBefore = cashBalance();
        UUID chequeId = open(sold).getFirst().id();
        UUID noteId = open(bought).getFirst().id();

        InstallmentResponse r = paymentPlanService.endorse(chequeId, new EndorseRequest(mehmet, noteId, today));
        assertThat(r.status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(r.endorsedTo().id()).isEqualTo(mehmet);
        assertThat(balance(ali)).as("Ali'nin borcu cekle kapandi").isZero();
        assertThat(balance(mehmet)).as("Mehmet'e kalan borc").isEqualTo(-300_000 * TL);
        assertThat(cashBalance()).as("kasaya para girmedi, cikmadi").isEqualTo(cashBefore);
        List<InstallmentResponse> purchase = installments(bought);
        assertThat(purchase).extracting(InstallmentResponse::amountCents).containsExactlyInAnyOrder(500_000 * TL, 300_000 * TL);
        assertThat(purchase).filteredOn(i -> i.status() == InstallmentStatus.PAID).extracting(InstallmentResponse::amountCents)
                .containsExactly(500_000 * TL);
        check("ciro");

        // Alis tarafindan geri al: ciro butunuyle geri alinir
        UUID paidNote = purchase.stream().filter(i -> i.status() == InstallmentStatus.PAID).findFirst().orElseThrow().id();
        paymentPlanService.undo(paidNote);
        assertThat(balance(ali)).isEqualTo(500_000 * TL);
        assertThat(balance(mehmet)).isEqualTo(-800_000 * TL);
        assertThat(installments(sold).getFirst().endorsedTo()).isNull();
        assertThat(installments(bought)).allSatisfy(i -> assertThat(i.status()).isNotEqualTo(InstallmentStatus.PAID));
        check("ciro geri");
    }

    @Test
    void ciro_cariye_hedefTaksitYoksaKisininCarisineOdemeYazilir() {
        UUID sold = cashVehicle(400_000 * TL);
        sell(sold, ali, List.of(cheque(today.plusDays(45), 500_000 * TL, "888")));
        paymentPlanService.endorse(open(sold).getFirst().id(), new EndorseRequest(veli, null, today));
        assertThat(balance(ali)).isZero();
        assertThat(balance(veli)).as("Veli'ye cek verdik: bize borclu").isEqualTo(500_000 * TL);
        check("cariye ciro");
    }

    @Test
    void karsiliksiz_ciroluCek_ciroGeriAlinir_borclarYenidenAcilir() {
        UUID bought = plannedVehicle(500_000 * TL, mehmet);
        UUID sold = cashVehicle(400_000 * TL);
        sell(sold, ali, List.of(cheque(today.plusDays(10), 500_000 * TL, "999")));
        UUID chequeId = open(sold).getFirst().id();
        paymentPlanService.endorse(chequeId, new EndorseRequest(mehmet, open(bought).getFirst().id(), today));
        check("ciro");

        InstallmentResponse r = paymentPlanService.bounce(chequeId);
        assertThat(r.bounced()).isTrue();
        assertThat(r.status()).isNotEqualTo(InstallmentStatus.PAID);
        assertThat(balance(ali)).as("Ali yine borclu").isEqualTo(500_000 * TL);
        assertThat(balance(mehmet)).as("Mehmet'e yine borcluyuz").isEqualTo(-500_000 * TL);
        assertThat(open(bought)).hasSize(1);
        check("karsiliksiz");

        // Ali sonradan nakit oderse karsiliksiz isareti kalkar
        InstallmentResponse paid = paymentPlanService.collect(chequeId, new CollectRequest(cash, today));
        assertThat(paid.bounced()).isFalse();
        check("sonradan tahsil");
    }

    @Test
    void karsiliksiz_kasayaTahsilEdilmisCek_tahsilatGeriAlinir() {
        UUID sold = cashVehicle(400_000 * TL);
        sell(sold, ali, List.of(cheque(today, 500_000 * TL, "555")));
        UUID chequeId = open(sold).getFirst().id();
        paymentPlanService.collect(chequeId, new CollectRequest(cash, today));
        long cashAfter = cashBalance();

        paymentPlanService.bounce(chequeId);
        assertThat(cashBalance()).isEqualTo(cashAfter - 500_000 * TL);
        assertThat(balance(ali)).isEqualTo(500_000 * TL);
        check("karsiliksiz");
    }

    @Test
    void kismiTahsilat_senetBolunur_kalaniAyniVadeyleAcikKalir_cekBolunemez() {
        UUID sold = cashVehicle(400_000 * TL);
        sell(sold, ali, List.of(
                new InstallmentRequest(today.plusDays(20), 600_000 * TL, PaymentMethod.PROMISSORY_NOTE, "S-1", null, false, null),
                cheque(today.plusDays(40), 100_000 * TL, "444")));
        InstallmentResponse note = open(sold).stream().filter(i -> i.method() == PaymentMethod.PROMISSORY_NOTE).findFirst().orElseThrow();

        paymentPlanService.collect(note.id(), new CollectRequest(cash, today, 200_000 * TL));
        List<InstallmentResponse> notes = installments(sold).stream().filter(i -> i.method() == PaymentMethod.PROMISSORY_NOTE).toList();
        assertThat(notes).extracting(InstallmentResponse::amountCents).containsExactlyInAnyOrder(200_000 * TL, 400_000 * TL);
        assertThat(notes).filteredOn(i -> i.status() != InstallmentStatus.PAID)
                .allSatisfy(i -> assertThat(i.dueDate()).isEqualTo(today.plusDays(20)));
        assertThat(balance(ali)).isEqualTo(500_000 * TL);
        check("kismi");

        UUID chequeId = open(sold).stream().filter(i -> i.method() == PaymentMethod.CHEQUE).findFirst().orElseThrow().id();
        expectError(1050, () -> paymentPlanService.collect(chequeId, new CollectRequest(cash, today, 50_000 * TL)));
        UUID restNote = notes.stream().filter(i -> i.status() != InstallmentStatus.PAID).findFirst().orElseThrow().id();
        expectError(1050, () -> paymentPlanService.collect(restNote, new CollectRequest(cash, today, 900_000 * TL)));
    }

    @Test
    void ciroKurallari_nakitTaksitCiroEdilmez_ayniKisiyeVeKucukSenedeVerilmez_portfoyHareketiKasadanDuzeltilmez() {
        UUID bought = plannedVehicle(300_000 * TL, mehmet);
        UUID sold = cashVehicle(400_000 * TL);
        sell(sold, ali, List.of(
                new InstallmentRequest(today.plusDays(5), 100_000 * TL, PaymentMethod.TRANSFER, null, null, false, null),
                cheque(today.plusDays(30), 500_000 * TL, "321")));
        UUID transfer = open(sold).stream().filter(i -> i.method() == PaymentMethod.TRANSFER).findFirst().orElseThrow().id();
        UUID chequeId = open(sold).stream().filter(i -> i.method() == PaymentMethod.CHEQUE).findFirst().orElseThrow().id();

        expectError(1048, () -> paymentPlanService.endorse(transfer, new EndorseRequest(mehmet, null, today)));
        expectError(1049, () -> paymentPlanService.endorse(chequeId, new EndorseRequest(ali, null, today)));
        UUID small = open(bought).getFirst().id();
        expectError(1049, () -> paymentPlanService.endorse(chequeId, new EndorseRequest(mehmet, small, today)));

        InstallmentResponse r = paymentPlanService.endorse(chequeId, new EndorseRequest(veli, null, today));
        expectError(1023, () -> ledgerService.correct(r.paidEntryId()));
        check("kurallar");
    }

    // ------------------------------------------------------------------ yardimcilar

    private UUID person(String name) {
        return contactService.create(new ContactRequest(name, null, ContactType.PERSON, null)).id();
    }

    private InstallmentRequest cheque(LocalDate due, long cents, String no) {
        return new InstallmentRequest(due, cents, PaymentMethod.CHEQUE, no, null, false, null, "Ziraat", "Ayşe Kaya");
    }

    private UUID cashVehicle(long price) {
        return vehicleService.create(request(price, cash, null, null)).vehicle().id();
    }

    /** Saticiya senetli alis: tek senet, 20 gun vadeli, odenmemis. */
    private UUID plannedVehicle(long price, UUID seller) {
        return vehicleService.create(request(price, null, seller, List.of(
                new InstallmentRequest(today.plusDays(20), price, PaymentMethod.PROMISSORY_NOTE, "A-1", null, false, null)))).vehicle().id();
    }

    private VehicleRequest request(long price, UUID account, UUID seller, List<InstallmentRequest> plan) {
        return new VehicleRequest("34 CK " + (100 + ++plates), "Fiat", "Egea", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL,
                10_000, null, null, null, today, seller, null, price, price, account, plan, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private void sell(UUID vehicle, UUID buyer, List<InstallmentRequest> rows) {
        long total = rows.stream().mapToLong(InstallmentRequest::amountCents).sum();
        vehicleService.sell(vehicle, new VehicleSaleRequest(today, buyer, null, total, rows));
    }

    private List<InstallmentResponse> installments(UUID vehicle) {
        return paymentPlanService.plans(vehicle).stream().flatMap(p -> p.installments().stream()).toList();
    }

    private List<InstallmentResponse> open(UUID vehicle) {
        return installments(vehicle).stream().filter(i -> i.status() != InstallmentStatus.PAID).toList();
    }

    private long balance(UUID contact) {
        return contactBalance(contact);
    }

    private long contactBalance(UUID contact) {
        Long b = jdbc.queryForObject("""
                select coalesce((select sum(e.amount_cents) from ledger_entry e where e.target_account_id = a.id), 0)
                     - coalesce((select sum(e.amount_cents) from ledger_entry e where e.source_account_id = a.id), 0)
                from account a where a.tenant_id = ? and a.type = 'BALANCE' and a.contact_id = ?
                """, Long.class, tenant, contact);
        return b == null ? 0 : b;
    }

    private long cashBalance() {
        return ledgerService.moneyAccounts().stream().filter(a -> a.id().equals(cash)).findFirst().orElseThrow().balanceCents();
    }

    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService).check(tenant, step);
        Long portfolio = jdbc.queryForObject("""
                select coalesce(sum(case when e.target_account_id = a.id then e.amount_cents else -e.amount_cents end), 0)
                from account a join ledger_entry e on e.target_account_id = a.id or e.source_account_id = a.id
                where a.tenant_id = ? and a.type = 'PORTFOLIO'
                """, Long.class, tenant);
        assertThat(portfolio).as("%s: portfoy bakiyesi", step).isZero();
    }

    private static void expectError(int code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApplicationRuntimeException.class)
                .extracting(e -> ((ApplicationRuntimeException) e).getError_code()).isEqualTo(code);
    }
}
