package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InstallmentAlreadyPaidException;
import com.galeri.app.exception.runtime.InvalidPayerException;
import com.galeri.app.exception.runtime.PurchaseHasPaymentsException;
import com.galeri.app.exception.runtime.PurchaseTotalMismatchException;
import com.galeri.app.exception.runtime.SellerRequiredException;
import com.galeri.app.exception.runtime.SaleHasPaymentsException;
import com.galeri.app.exception.runtime.SaleTotalMismatchException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PaymentPlan;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.PaymentPlanRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.service.LedgerService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)   // ortak hazirliklar (save, getReferenceById) her testte kullanilmiyor
class PaymentPlanServiceImplTest {

    @Mock private PaymentPlanRepository planRepository;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private PanelUserRepository panelUserRepository;
    @Mock private LedgerService ledgerService;

    // "Su an": 1 Temmuz 2026, 09:00 Istanbul
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T06:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID cashId = UUID.randomUUID();
    private PaymentPlanServiceImpl service;

    private Vehicle vehicle;
    private Contact buyer;
    private Contact evim;

    @BeforeEach
    void setUp() {
        service = new PaymentPlanServiceImpl(planRepository, installmentRepository, contactRepository, vehicleRepository,
                accountRepository, entryRepository, panelUserRepository, ledgerService, clock);
        TestSession.open(UUID.randomUUID(), tenantId, "ADMIN");

        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setTenantId(tenantId);
        vehicle.setPlate("34 KLM 4521");
        vehicle.setMake("Renault");
        vehicle.setModel("Clio");
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        buyer = contact("Ayse Demir", ContactType.PERSON);
        evim = contact("Eminevim", ContactType.ORGANIZATION);

        when(planRepository.save(any(PaymentPlan.class))).thenAnswer(inv -> {
            PaymentPlan p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(installmentRepository.save(any(Installment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(installmentRepository.markPaid(any(), any(), any())).thenReturn(1);   // kosullu guncelleme: taksit hala odenmemis
        when(ledgerService.writeSale(any(), any(), anyLong(), any())).thenAnswer(inv -> entry());
        when(ledgerService.writeSettlement(any(), any(), any(), any(), anyLong(), any(), any())).thenAnswer(inv -> entry());
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    // ------------------------------------------------------------------ satis

    @Test
    void taksitToplamiSatisBedelineEsitDegilse_satisReddedilir() {
        VehicleSaleRequest r = sale(100000L, row(60000L, PaymentMethod.CASH, null, true), row(39999L, PaymentMethod.PROMISSORY_NOTE, null, false));

        assertThatThrownBy(() -> service.recordSale(vehicle, buyer, r)).isInstanceOf(SaleTotalMismatchException.class);
        verify(ledgerService, never()).writeSale(any(), any(), anyLong(), any());
        verify(planRepository, never()).save(any());
    }

    @Test
    void evimKismi_kurumunCarisineYazilir_pesinatAlicininCarisine() {
        VehicleSaleRequest r = sale(1000000L,
                row(200000L, PaymentMethod.CASH, null, true),
                row(800000L, PaymentMethod.INSTITUTION, evim.getId(), false));

        service.recordSale(vehicle, buyer, r);

        verify(ledgerService).writeSale(vehicle, buyer, 200000L, TODAY);
        verify(ledgerService).writeSale(vehicle, evim, 800000L, TODAY);
        verify(ledgerService, times(2)).writeSale(any(), any(), anyLong(), any());
    }

    @Test
    void pesinat_ayniIslemdeTahsilEdilir_digerTaksitBekler() {
        VehicleSaleRequest r = sale(300000L,
                row(100000L, PaymentMethod.CASH, null, true),
                row(200000L, PaymentMethod.PROMISSORY_NOTE, null, false));

        service.recordSale(vehicle, buyer, r);

        verify(ledgerService).writeSettlement(PlanDirection.SALE, vehicle, buyer, cashId, 100000L, TODAY, "Peşinat");
        ArgumentCaptor<Installment> saved = ArgumentCaptor.forClass(Installment.class);
        verify(installmentRepository, times(2)).save(saved.capture());
        Installment first = saved.getAllValues().get(0);
        Installment second = saved.getAllValues().get(1);
        assertThat(first.getSeq()).isEqualTo((short) 1);
        assertThat(first.isPaid()).isTrue();
        assertThat(first.getPaidAt()).isNotNull();
        assertThat(second.isPaid()).isFalse();
        assertThat(second.getPayer()).isSameAs(buyer);
        assertThat(second.getPlan().getTotalCents()).isEqualTo(300000L);
    }

    @Test
    void kurumTaksitinde_kurumSecilmezse_reddedilir() {
        VehicleSaleRequest noPayer = sale(100L, row(100L, PaymentMethod.INSTITUTION, null, false));
        VehicleSaleRequest personPayer = sale(100L, row(100L, PaymentMethod.INSTITUTION, buyer.getId(), false));

        assertThatThrownBy(() -> service.recordSale(vehicle, buyer, noPayer)).isInstanceOf(InvalidPayerException.class);
        assertThatThrownBy(() -> service.recordSale(vehicle, buyer, personPayer)).isInstanceOf(InvalidPayerException.class);
        verify(ledgerService, never()).writeSale(any(), any(), anyLong(), any());
    }

    // ------------------------------------------------------------------ satisi geri al

    @Test
    void odenmisTaksitiOlanSatis_geriAlinamaz() {
        PaymentPlan plan = plan();
        when(planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.SALE)).thenReturn(Optional.of(plan));
        when(installmentRepository.existsByPlan_IdAndPaidEntryIdIsNotNull(plan.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.undoSale(vehicle)).isInstanceOf(SaleHasPaymentsException.class);
        verify(ledgerService, never()).reverseVehicleSales(any());
        verify(planRepository, never()).delete(any());
    }

    @Test
    void odenmemisSatis_geriAlininca_planSilinir_satisHareketleriTerslenir() {
        PaymentPlan plan = plan();
        when(planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.SALE)).thenReturn(Optional.of(plan));
        when(installmentRepository.existsByPlan_IdAndPaidEntryIdIsNotNull(plan.getId())).thenReturn(false);

        service.undoSale(vehicle);

        verify(planRepository).delete(plan);
        verify(ledgerService).reverseVehicleSales(vehicle.getId());
    }

    // ------------------------------------------------------------------ tahsilat

    @Test
    void tahsilat_hareketYazarTaksitiKapatir_geriAlmaTerslerVeAcar() {
        Installment i = installment(plan(), LocalDate.of(2026, 6, 30), 50000L, false);
        when(installmentRepository.findById(i.getId())).thenReturn(Optional.of(i));
        when(installmentRepository.findByPlanIds(anyCollection())).thenReturn(List.of(i));

        InstallmentResponse collected = service.collect(i.getId(), new CollectRequest(cashId, TODAY));

        verify(ledgerService).writeSettlement(PlanDirection.SALE, vehicle, buyer, cashId, 50000L, TODAY, "Senet tahsilatı");
        assertThat(collected.status()).isEqualTo(InstallmentStatus.PAID);
        UUID paidEntry = i.getPaidEntryId();
        assertThat(paidEntry).isNotNull();

        InstallmentResponse undone = service.undo(i.getId());

        verify(ledgerService).reverse(paidEntry);
        assertThat(i.isPaid()).isFalse();
        assertThat(i.getPaidAt()).isNull();
        assertThat(undone.status()).isEqualTo(InstallmentStatus.OVERDUE);
    }

    @Test
    void odenmisTaksit_tekrarTahsilEdilemez() {
        Installment i = installment(plan(), TODAY, 50000L, true);
        when(installmentRepository.findById(i.getId())).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.collect(i.getId(), new CollectRequest(cashId, TODAY)))
                .isInstanceOf(InstallmentAlreadyPaidException.class);
        verify(ledgerService, never()).writeSettlement(any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void ayniAndaBaskaIstekTahsilEttiyse_kosulluGuncellemeSifirSatir_1019() {
        Installment i = installment(plan(), TODAY, 50000L, false);   // okunurken odenmemis gorunuyor
        when(installmentRepository.findById(i.getId())).thenReturn(Optional.of(i));
        when(installmentRepository.markPaid(any(), any(), any())).thenReturn(0);   // ama diger istek once yazdi

        assertThatThrownBy(() -> service.collect(i.getId(), new CollectRequest(cashId, TODAY)))
                .isInstanceOf(InstallmentAlreadyPaidException.class);
        assertThat(i.isPaid()).isFalse();   // bellekteki nesneye yazilmaz; hata transaction'i geri alir
    }

    @Test
    void baskaGalerininTaksiti_bulunamadiGibiDavranir() {
        Installment i = installment(plan(), TODAY, 50000L, false);
        i.setTenantId(UUID.randomUUID());
        when(installmentRepository.findById(i.getId())).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.collect(i.getId(), new CollectRequest(cashId, TODAY)))
                .isInstanceOf(SourceNotBelongToTenantException.class);
    }

    // ------------------------------------------------------------------ alis

    private static final LocalDate BOUGHT = LocalDate.of(2026, 6, 10);

    private Contact purchasable(long price) {
        Contact seller = contact("Mehmet Yilmaz", ContactType.PERSON);
        vehicle.setSeller(seller);
        vehicle.setActualPurchaseCents(price);
        vehicle.setPurchaseDate(BOUGHT);
        return seller;
    }

    private InstallmentRequest purchaseRow(LocalDate due, long amount, boolean paidNow) {
        return new InstallmentRequest(due, amount, PaymentMethod.PROMISSORY_NOTE, null, null, paidNow, paidNow ? cashId : null);
    }

    private PaymentPlan purchasePlan(Contact seller, long total) {
        PaymentPlan p = plan();
        p.setDirection(PlanDirection.PURCHASE);
        p.setContact(seller);
        p.setTotalCents(total);
        when(planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.PURCHASE)).thenReturn(Optional.of(p));
        return p;
    }

    private Installment purchaseInstallment(PaymentPlan p, Contact seller, int seq, LocalDate due, long amount, boolean paid) {
        Installment i = installment(p, due, amount, paid);
        i.setSeq((short) seq);
        i.setPayer(seller);
        return i;
    }

    private LedgerEntry plannedPurchaseEntry(Contact seller, long amount) {
        Account balance = new Account();
        balance.setId(UUID.randomUUID());
        balance.setType(AccountType.BALANCE);
        balance.setContactId(seller.getId());
        LedgerEntry e = entry();
        e.setSourceAccount(balance);
        e.setAmountCents(amount);
        e.setEntryDate(BOUGHT);
        return e;
    }

    @Test
    void planliAlis_saticininCarisindenAracaYazilir_pesinatAyniIslemdeOdenir() {
        Contact seller = purchasable(1600000L);
        when(planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.PURCHASE)).thenReturn(Optional.empty());
        when(ledgerService.activeVehiclePurchases(vehicle.getId())).thenReturn(List.of());
        when(ledgerService.writePlannedPurchase(any(), any(), anyLong(), any())).thenAnswer(inv -> entry());

        service.syncPurchase(vehicle, null, List.of(
                purchaseRow(BOUGHT, 600000L, true),
                purchaseRow(LocalDate.of(2026, 7, 10), 500000L, false),
                purchaseRow(LocalDate.of(2026, 8, 10), 500000L, false)));

        verify(ledgerService).writePlannedPurchase(vehicle, seller, 1600000L, BOUGHT);
        verify(ledgerService).writeSettlement(PlanDirection.PURCHASE, vehicle, seller, cashId, 600000L, BOUGHT, "Peşinat");
        verify(ledgerService, never()).syncVehiclePurchase(any(), any());
        ArgumentCaptor<PaymentPlan> plan = ArgumentCaptor.forClass(PaymentPlan.class);
        verify(planRepository).save(plan.capture());
        assertThat(plan.getValue().getDirection()).isEqualTo(PlanDirection.PURCHASE);
        assertThat(plan.getValue().getContact()).isSameAs(seller);
        ArgumentCaptor<Installment> saved = ArgumentCaptor.forClass(Installment.class);
        verify(installmentRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Installment::isPaid).containsExactly(true, false, false);
        assertThat(saved.getAllValues()).extracting(Installment::getSeq).containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    void alisTaksitiOdenince_kasadanSaticininCarisineOdemeYazilir() {
        Contact seller = purchasable(1600000L);
        PaymentPlan p = purchasePlan(seller, 1600000L);
        Installment i = purchaseInstallment(p, seller, 2, TODAY, 500000L, false);
        when(installmentRepository.findById(i.getId())).thenReturn(Optional.of(i));
        when(installmentRepository.findByPlanIds(anyCollection())).thenReturn(List.of(i));

        InstallmentResponse paid = service.collect(i.getId(), new CollectRequest(cashId, TODAY));

        verify(ledgerService).writeSettlement(PlanDirection.PURCHASE, vehicle, seller, cashId, 500000L, TODAY, "Senet ödemesi");
        assertThat(paid.status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(paid.direction()).isEqualTo(PlanDirection.PURCHASE);
    }

    @Test
    void odenmisTaksitVarken_alisBedeliDegistirilemez() {
        Contact seller = purchasable(1700000L);   // plan 1.600.000 ile kurulmustu
        PaymentPlan p = purchasePlan(seller, 1600000L);
        when(installmentRepository.findByPlanIds(anyCollection())).thenReturn(List.of(
                purchaseInstallment(p, seller, 1, BOUGHT, 600000L, true),
                purchaseInstallment(p, seller, 2, LocalDate.of(2026, 7, 10), 1000000L, false)));

        assertThatThrownBy(() -> service.syncPurchase(vehicle, null, List.of(purchaseRow(LocalDate.of(2026, 7, 10), 1100000L, false))))
                .isInstanceOf(PurchaseHasPaymentsException.class);
        verify(ledgerService, never()).reverseVehiclePurchases(any());
        verify(installmentRepository, never()).deleteAll(any());
    }

    @Test
    void odenmisTaksitVarken_pesinAlisaGecilemez() {
        Contact seller = purchasable(1600000L);
        PaymentPlan p = purchasePlan(seller, 1600000L);
        when(installmentRepository.findByPlanIds(anyCollection())).thenReturn(List.of(purchaseInstallment(p, seller, 1, BOUGHT, 600000L, true)));

        assertThatThrownBy(() -> service.syncPurchase(vehicle, cashId, null)).isInstanceOf(PurchaseHasPaymentsException.class);
        verify(ledgerService, never()).syncVehiclePurchase(any(), any());
    }

    @Test
    void planDegismediyse_defterVeTaksitlereDokunulmaz() {
        Contact seller = purchasable(1600000L);
        PaymentPlan p = purchasePlan(seller, 1600000L);
        when(installmentRepository.findByPlanIds(anyCollection())).thenReturn(List.of(
                purchaseInstallment(p, seller, 1, BOUGHT, 600000L, true),
                purchaseInstallment(p, seller, 2, LocalDate.of(2026, 7, 10), 500000L, false),
                purchaseInstallment(p, seller, 3, LocalDate.of(2026, 8, 10), 500000L, false)));
        when(ledgerService.activeVehiclePurchases(vehicle.getId())).thenReturn(List.of(plannedPurchaseEntry(seller, 1600000L)));

        service.syncPurchase(vehicle, null, List.of(
                purchaseRow(LocalDate.of(2026, 7, 10), 500000L, false),
                purchaseRow(LocalDate.of(2026, 8, 10), 500000L, false)));

        verify(ledgerService, never()).reverseVehiclePurchases(any());
        verify(ledgerService, never()).writePlannedPurchase(any(), any(), anyLong(), any());
        verify(installmentRepository, never()).save(any());
        verify(installmentRepository, never()).deleteAll(any());
    }

    @Test
    void vadeDegisince_sadeceOdenmemisTaksitlerYenidenYazilir_defterAyniKalir() {
        Contact seller = purchasable(1600000L);
        PaymentPlan p = purchasePlan(seller, 1600000L);
        Installment paid = purchaseInstallment(p, seller, 1, BOUGHT, 600000L, true);
        Installment second = purchaseInstallment(p, seller, 2, LocalDate.of(2026, 7, 10), 500000L, false);
        Installment third = purchaseInstallment(p, seller, 3, LocalDate.of(2026, 8, 10), 500000L, false);
        when(installmentRepository.findByPlanIds(anyCollection())).thenReturn(List.of(paid, second, third));
        when(ledgerService.activeVehiclePurchases(vehicle.getId())).thenReturn(List.of(plannedPurchaseEntry(seller, 1600000L)));

        service.syncPurchase(vehicle, null, List.of(
                purchaseRow(LocalDate.of(2026, 7, 20), 500000L, false),
                purchaseRow(LocalDate.of(2026, 8, 20), 500000L, false)));

        verify(ledgerService, never()).reverseVehiclePurchases(any());
        verify(installmentRepository).deleteAll(List.of(second, third));
        ArgumentCaptor<Installment> saved = ArgumentCaptor.forClass(Installment.class);
        verify(installmentRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Installment::getSeq).containsExactly((short) 2, (short) 3);
        assertThat(saved.getAllValues()).extracting(Installment::getDueDate)
                .containsExactly(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 20));
    }

    @Test
    void alisPlaniToplamiBedeleEsitDegilse_yaDaSaticiYoksa_reddedilir() {
        purchasable(1600000L);
        when(planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.PURCHASE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncPurchase(vehicle, null, List.of(purchaseRow(BOUGHT, 1500000L, false))))
                .isInstanceOf(PurchaseTotalMismatchException.class);
        vehicle.setSeller(null);
        assertThatThrownBy(() -> service.syncPurchase(vehicle, null, List.of(purchaseRow(BOUGHT, 1600000L, false))))
                .isInstanceOf(SellerRequiredException.class);
        verify(ledgerService, never()).writePlannedPurchase(any(), any(), anyLong(), any());
    }

    @Test
    void planYoksaPesinAlis_bugunkuAkistanGider() {
        purchasable(1600000L);
        when(planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.PURCHASE)).thenReturn(Optional.empty());

        service.syncPurchase(vehicle, cashId, List.of());

        verify(ledgerService).syncVehiclePurchase(vehicle, cashId);
        verify(ledgerService, never()).reverseVehiclePurchases(any());
    }

    // ------------------------------------------------------------------ liste

    @Test
    void gecikenFiltresi_Clocka_goreCalisir_bugunVadeliGecikmisSayilmaz() {
        PaymentPlan plan = plan();
        when(installmentRepository.findAllWithPlan(tenantId)).thenReturn(List.of(
                installment(plan, LocalDate.of(2026, 6, 28), 100L, false),   // 3 gun gecikmis
                installment(plan, TODAY, 200L, false),                       // bugun: bekliyor
                installment(plan, LocalDate.of(2026, 7, 10), 300L, false),
                installment(plan, LocalDate.of(2026, 6, 20), 400L, true)));  // odenmis
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));

        List<InstallmentResponse> overdue = service.list(InstallmentStatus.OVERDUE, null, null, null);
        List<InstallmentResponse> upcoming = service.list(InstallmentStatus.UPCOMING, PlanDirection.SALE, null, null);
        List<InstallmentResponse> paid = service.list(InstallmentStatus.PAID, null, null, null);

        assertThat(overdue).singleElement().satisfies(i -> {
            assertThat(i.amountCents()).isEqualTo(100L);
            assertThat(i.daysLate()).isEqualTo(3);
            assertThat(i.vehicle().plate()).isEqualTo("34 KLM 4521");
            assertThat(i.count()).isEqualTo(4);
        });
        assertThat(upcoming).extracting(InstallmentResponse::amountCents).containsExactly(200L, 300L);
        assertThat(paid).extracting(InstallmentResponse::amountCents).containsExactly(400L);
        assertThat(service.list(null, PlanDirection.PURCHASE, null, null)).isEmpty();
    }

    // ------------------------------------------------------------------ yardimcilar

    private VehicleSaleRequest sale(long price, InstallmentRequest... rows) {
        return new VehicleSaleRequest(TODAY, buyer.getId(), null, price, List.of(rows));
    }

    private InstallmentRequest row(long amount, PaymentMethod method, UUID payerId, boolean paidNow) {
        return new InstallmentRequest(TODAY, amount, method, null, payerId, paidNow, paidNow ? cashId : null);
    }

    private Contact contact(String name, ContactType type) {
        Contact c = new Contact();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setName(name);
        c.setType(type);
        when(contactRepository.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private PaymentPlan plan() {
        PaymentPlan p = new PaymentPlan();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setVehicleId(vehicle.getId());
        p.setDirection(PlanDirection.SALE);
        p.setContact(buyer);
        p.setTotalCents(1000000L);
        return p;
    }

    private Installment installment(PaymentPlan plan, LocalDate due, long amount, boolean paid) {
        Installment i = new Installment();
        i.setId(UUID.randomUUID());
        i.setTenantId(tenantId);
        i.setPlan(plan);
        i.setSeq((short) 1);
        i.setDueDate(due);
        i.setAmountCents(amount);
        i.setMethod(PaymentMethod.PROMISSORY_NOTE);
        i.setPayer(buyer);
        if (paid) {
            i.setPaidEntryId(UUID.randomUUID());
            i.setPaidAt(OffsetDateTime.now(clock));
        }
        return i;
    }

    private static LedgerEntry entry() {
        LedgerEntry e = new LedgerEntry();
        e.setId(UUID.randomUUID());
        return e;
    }
}
