package com.galeri.app;

import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.ContactPaymentRequest;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.LedgerInvariants;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Para dogrulugu (revizyon "her PR icin zorunlu"): gercek Postgres'te bastan sona bir galeri senaryosu servisler
// uzerinden oynanir; her adimdan sonra defter degismezleri kontrol edilir. Beklenen tutarlar burada sabit degil:
// TC0-TC13 altin senaryo (sabit beklenen degerlerle) ayri bir test olarak eklenecek.
@SpringBootTest
class MoneyInvariantsIT {

    private static final long TL = 100;   // kurus

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private ContactService contactService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private VehicleService vehicleService;
    @Autowired private PaymentPlanService paymentPlanService;
    @Autowired private com.galeri.app.service.NetWorthService netWorthService;

    private UUID tenant;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Defter Testi')", tenant, "inv-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "91" + suffix);
        TestSession.open(user, tenant, "ADMIN");
        today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")));
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void galeriSenaryosu_herAdimdanSonraDefterDegismezleriTutar() {
        UUID partnerA = contact("Ortak A", ContactType.PERSON);
        UUID partnerB = contact("Ortak B", ContactType.PERSON);
        UUID seller = contact("Satici", ContactType.PERSON);
        UUID buyer = contact("Alici", ContactType.PERSON);
        UUID consignor = contact("Konsinye Sahibi", ContactType.PERSON);
        UUID supplier = contact("Eski Tedarikci", ContactType.ORGANIZATION);
        UUID cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        UUID bank = ledgerService.addBank(new AccountRequest("Ziraat")).id();
        check("baslangic");

        // Sermaye girisi ve virman
        capitalService.addPartner(new CapitalRequests.Partner(partnerA));
        capitalService.addPartner(new CapitalRequests.Partner(partnerB));
        capitalService.setOwner(partnerA);
        capitalService.move(partnerA, new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, day(-60), 1_000_000 * TL, null));
        capitalService.move(partnerB, new CapitalRequests.Movement(CapitalRequests.Direction.IN, bank, day(-60), 500_000 * TL, null));
        check("sermaye girisi");
        ledgerService.transfer(new CapitalRequests.Transfer(cash, bank, day(-59), 100_000 * TL, null));
        check("virman");

        // Acilis bakiyesi: tedarikciye borcumuz
        ledgerService.openingBalance(new CapitalRequests.Opening(null, supplier, CapitalRequests.OpeningSide.PAYABLE, day(-60), 20_000 * TL));
        check("acilis bakiyesi");

        // Pesin alis + arac masrafi (ekle, duzenle, sil)
        UUID passat = vehicleService.create(vehicle("34 INV 101", "Volkswagen", "Passat 1.5 TSI", day(-50), seller,
                600_000 * TL, cash, null, Ownership.DEALER, null)).vehicle().id();
        check("pesin alis");
        UUID paint = ledgerService.addExpense(expense(day(-45), cash, 15_000 * TL, passat, "Boya")).id();
        ledgerService.updateExpense(paint, expense(day(-45), cash, 12_500_50, passat, "Boya"));
        UUID tire = ledgerService.addExpense(expense(day(-44), bank, 7_000 * TL, passat, "Lastik")).id();
        ledgerService.deleteExpense(tire);
        check("arac masrafi ekle / duzenle / sil");

        // Taksitli satis: pesinat simdi, iki senet
        vehicleService.sell(passat, new VehicleSaleRequest(day(-30), buyer, null, 750_000 * TL, List.of(
                installment(day(-30), 250_000 * TL, PaymentMethod.CASH, true, cash),
                installment(day(0), 250_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null),
                installment(day(30), 250_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null))));
        check("taksitli satis");
        UUID note1 = installments(passat).get(1).id();
        paymentPlanService.collect(note1, new CollectRequest(bank, day(0)));
        check("senet tahsilati");
        paymentPlanService.undo(note1);
        check("tahsilat geri alma");
        paymentPlanService.collect(note1, new CollectRequest(cash, day(0)));
        check("yeniden tahsilat");

        // Planli alis: pesinat simdi, kalan senet; senet bankadan odenir
        UUID clio = vehicleService.create(vehicle("34 INV 102", "Renault", "Clio 1.0 TCe", day(-20), seller,
                400_000 * TL, null, List.of(
                        installment(day(-20), 100_000 * TL, PaymentMethod.CASH, true, cash),
                        installment(day(10), 300_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null)),
                Ownership.DEALER, null)).vehicle().id();
        check("planli alis");
        paymentPlanService.collect(installments(clio).get(1).id(), new CollectRequest(bank, day(-5)));
        check("alis senedi odemesi");

        // Satip geri alma: hic taksit odenmemis satis
        vehicleService.sell(clio, new VehicleSaleRequest(day(-3), buyer, null, 480_000 * TL, List.of(
                installment(day(20), 480_000 * TL, PaymentMethod.PROMISSORY_NOTE, false, null))));
        check("ikinci satis");
        vehicleService.undoSale(clio);
        check("satisi geri alma");

        // Konsinye: komisyon %5, sahibine masraf yansitilir, sahibine kismi odeme
        UUID egea = vehicleService.create(vehicle("34 INV 103", "Fiat", "Egea 1.3 Multijet", day(-15), null,
                null, null, null, Ownership.CONSIGNMENT, consignor)).vehicle().id();
        ledgerService.addExpense(new ExpenseRequest(day(-14), cash, 2_000 * TL, egea, null, "Yikama", null, null, true));
        ledgerService.addExpense(new ExpenseRequest(day(-14), cash, 1_000 * TL, egea, null, "Ilan", null, null, false));
        check("konsinye arac ve masraflari");
        vehicleService.sell(egea, new VehicleSaleRequest(day(-10), buyer, null, 300_000_33L, List.of(
                installment(day(-10), 300_000_33L, PaymentMethod.TRANSFER, true, bank))));
        check("konsinye satis");
        ledgerService.payContact(consignor, new ContactPaymentRequest(bank, day(-8), 200_000 * TL, null));
        check("konsinye sahibine kismi odeme");

        // Genel gider, kar payi, sermaye cekisi
        ledgerService.addExpense(expense(day(-7), cash, 8_000 * TL, null, "Kira"));
        check("genel gider");
        capitalService.allocate(new CapitalRequests.Allocation(partnerA, day(-2), 50_000 * TL, day(-60), day(0)));
        check("kar payi sermayeye");
        capitalService.move(partnerB, new CapitalRequests.Movement(CapitalRequests.Direction.OUT, cash, day(-1), 20_000 * TL, null));
        check("sermaye cekisi");

        // Hareket duzeltme (genel gider terslenir)
        UUID wrong = ledgerService.addExpense(expense(day(-1), bank, 999 * TL, null, "Kira")).id();
        ledgerService.correct(wrong);
        check("hareket duzeltme");
    }

    /** Her adimdan sonra: adim adi hata mesajinda gorunur. */
    private void check(String step) {
        new LedgerInvariants(jdbc, capitalService).check(tenant, step);
        org.assertj.core.api.Assertions.assertThat(netWorthService.summary().differenceCents())
                .as("%s: acilis ve duzeltmelerle varlik dengesi", step).isZero();
    }

    // ------------------------------------------------------------------ yardimcilar

    private LocalDate day(int offset) {
        return today.plusDays(offset);
    }

    private UUID contact(String name, ContactType type) {
        return contactService.create(new ContactRequest(name, null, type, null)).id();
    }

    private List<InstallmentResponse> installments(UUID vehicleId) {
        return paymentPlanService.plans(vehicleId).stream().map(PaymentPlanResponse::installments).findFirst().orElseThrow();
    }

    private static InstallmentRequest installment(LocalDate due, long cents, PaymentMethod method, boolean paidNow, UUID account) {
        return new InstallmentRequest(due, cents, method, null, null, paidNow, account);
    }

    private static ExpenseRequest expense(LocalDate date, UUID account, long cents, UUID vehicleId, String category) {
        return new ExpenseRequest(date, account, cents, vehicleId, null, category, null, null, null);
    }

    private static VehicleRequest vehicle(String plate, String make, String model, LocalDate purchaseDate, UUID seller,
                                          Long actualCents, UUID purchaseAccount, List<InstallmentRequest> plan,
                                          Ownership ownership, UUID consignor) {
        boolean consignment = ownership == Ownership.CONSIGNMENT;
        return new VehicleRequest(plate, make, model, (short) 2020, Fuel.GASOLINE, Transmission.MANUAL, 50_000, null, null, null,
                purchaseDate, seller, null, actualCents, actualCents, purchaseAccount, plan, ownership,
                consignor, consignment ? CommissionType.RATE : null, consignment ? 500 : null, null, null, null, null);
    }
}
