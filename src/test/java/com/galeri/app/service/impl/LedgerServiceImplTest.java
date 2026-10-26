package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CategoryDepthLimitException;
import com.galeri.app.exception.runtime.CommissionExceedsSaleException;
import com.galeri.app.exception.runtime.CorrectionNotCorrectableException;
import com.galeri.app.exception.runtime.EntryAlreadyCorrectedException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidCategoryException;
import com.galeri.app.exception.runtime.LinkedEntryException;
import com.galeri.app.exception.runtime.NotAnExpenseException;
import com.galeri.app.exception.runtime.OpeningBalanceExistsException;
import com.galeri.app.exception.runtime.SameAccountException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.ExpenseCategory;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.ContributionSource;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ContactPaymentRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.ShareResponse;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.ExpenseCategoryRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.PaymentPlanRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.service.CoOwnershipService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)   // ortak hazirliklar (save, getReferenceById) her testte kullanilmiyor
class LedgerServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private ExpenseCategoryRepository categoryRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private PanelUserRepository panelUserRepository;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private CoOwnershipService coOwnershipService;
    @Mock private ContactRepository contactRepository;
    @Mock private PaymentPlanRepository planRepository;

    private static final LocalDate DAY = LocalDate.of(2026, 7, 1);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private LedgerServiceImpl service;

    private Account cash;
    private Account expense;

    @BeforeEach
    void setUp() {
        service = new LedgerServiceImpl(accountRepository, entryRepository, categoryRepository, vehicleRepository, panelUserRepository,
                installmentRepository, contactRepository, coOwnershipService, planRepository);
        TestSession.open(userId, tenantId, "ADMIN");

        cash = account(AccountType.CASH, tenantId);
        expense = account(AccountType.EXPENSE, tenantId);
        when(accountRepository.findById(cash.getId())).thenReturn(Optional.of(cash));
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.EXPENSE)).thenReturn(Optional.of(expense));

        PanelUser me = new PanelUser();
        me.setId(userId);
        me.setFullName("Demo Yonetici");
        when(panelUserRepository.getReferenceById(userId)).thenReturn(me);

        when(entryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> {
            LedgerEntry e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(categoryRepository.save(any(ExpenseCategory.class))).thenAnswer(inv -> {
            ExpenseCategory c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    // ------------------------------------------------------------------ masraf

    @Test
    void aracMasrafi_kasadanAracHesabinaYazilir_kategoriYoksaIlkHarfiBuyukOlusur() {
        Vehicle vehicle = vehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(accountRepository.findByVehicleId(vehicle.getId())).thenReturn(Optional.empty());
        when(categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(tenantId, ExpenseRoot.VEHICLE, "boya"))
                .thenReturn(Optional.empty());

        LedgerEntryResponse result = service.addExpense(expenseRequest(cash.getId(), 500000L, vehicle.getId(), "  boya "));

        ArgumentCaptor<LedgerEntry> written = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(written.capture());
        LedgerEntry e = written.getValue();
        assertThat(e.getType()).isEqualTo(LedgerEntryType.VEHICLE_EXPENSE);
        assertThat(e.getSourceAccount()).isSameAs(cash);
        assertThat(e.getTargetAccount().getType()).isEqualTo(AccountType.VEHICLE);
        assertThat(e.getTargetAccount().getVehicleId()).isEqualTo(vehicle.getId());
        assertThat(e.getAmountCents()).isEqualTo(500000L);
        assertThat(e.getTenantId()).isEqualTo(tenantId);
        assertThat(e.getCreatedBy().getId()).isEqualTo(userId);
        assertThat(e.getCategory().getName()).isEqualTo("Boya");
        assertThat(e.getCategory().getRoot()).isEqualTo(ExpenseRoot.VEHICLE);
        assertThat(result.category()).isEqualTo("Boya");
        assertThat(result.vehicle().plate()).isEqualTo("34 KLM 4521");
    }

    @Test
    void varOlanKategoriAdi_harfFarkiylaYazilsaBile_tekrarOlusmaz() {
        ExpenseCategory rent = category(ExpenseRoot.GENERAL, "Kira", null);
        when(categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(tenantId, ExpenseRoot.GENERAL, "KİRA"))
                .thenReturn(Optional.of(rent));

        service.addExpense(expenseRequest(cash.getId(), 4000000L, null, "KİRA"));

        verify(categoryRepository, never()).save(any());
        ArgumentCaptor<LedgerEntry> written = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(written.capture());
        assertThat(written.getValue().getCategory()).isSameAs(rent);
    }

    @Test
    void genelGider_giderHesabinaYazilir() {
        when(categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());

        service.addExpense(expenseRequest(cash.getId(), 4000000L, null, "Kira"));

        ArgumentCaptor<LedgerEntry> written = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(written.capture());
        assertThat(written.getValue().getType()).isEqualTo(LedgerEntryType.GENERAL_EXPENSE);
        assertThat(written.getValue().getTargetAccount()).isSameAs(expense);
        assertThat(written.getValue().getVehicle()).isNull();
    }

    @Test
    void topluMasraf_herSatirAyriHareketOlarakSirayaGoreYazilir() {
        when(categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());

        List<LedgerEntryResponse> result = service.addExpenses(List.of(
                expenseRequest(cash.getId(), 4000000L, null, "Kira"),
                expenseRequest(cash.getId(), 1500000L, null, "Maaş"),
                expenseRequest(cash.getId(), 25000L, null, "Çay")));

        ArgumentCaptor<LedgerEntry> written = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository, times(3)).save(written.capture());
        assertThat(written.getAllValues()).extracting(LedgerEntry::getAmountCents).containsExactly(4000000L, 1500000L, 25000L);
        assertThat(written.getAllValues()).allSatisfy(e -> assertThat(e.getType()).isEqualTo(LedgerEntryType.GENERAL_EXPENSE));
        assertThat(result).extracting(LedgerEntryResponse::category).containsExactly("Kira", "Maaş", "Çay");
    }

    @Test
    void topluMasraf_hataliSatirdaDurur() {
        when(categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());

        // Ikinci satirin kategorisi bos: hata firlar, transaction geri alinir (ilk satir da yazilmamis olur)
        assertThatThrownBy(() -> service.addExpenses(List.of(
                expenseRequest(cash.getId(), 4000000L, null, "Kira"),
                expenseRequest(cash.getId(), 100L, null, " "))))
                .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    void masraf_kasaYaDaBankaDisindakiHesaptanOdenemez() {
        Account balance = account(AccountType.BALANCE, tenantId);
        when(accountRepository.findById(balance.getId())).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.addExpense(expenseRequest(balance.getId(), 100L, null, "Kira")))
                .isInstanceOf(InvalidAccountException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void baskaGalerininKasasi_bulunamadiGibiDavranir() {
        Account otherCash = account(AccountType.CASH, UUID.randomUUID());
        when(accountRepository.findById(otherCash.getId())).thenReturn(Optional.of(otherCash));

        assertThatThrownBy(() -> service.addExpense(expenseRequest(otherCash.getId(), 100L, null, "Kira")))
                .isInstanceOf(SourceNotBelongToTenantException.class);
    }

    @Test
    void kategorisizMasraf_reddedilir() {
        assertThatThrownBy(() -> service.addExpense(expenseRequest(cash.getId(), 100L, null, "  ")))
                .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    void genelGiderKategorisi_aracMasrafindaKullanilamaz() {
        Vehicle vehicle = vehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(accountRepository.findByVehicleId(vehicle.getId())).thenReturn(Optional.of(vehicleAccount(vehicle)));
        ExpenseCategory rent = category(ExpenseRoot.GENERAL, "Kira", null);
        when(categoryRepository.findById(rent.getId())).thenReturn(Optional.of(rent));

        ExpenseRequest request = new ExpenseRequest(DAY, cash.getId(), 100L, vehicle.getId(), rent.getId(), null, null, null, null);
        assertThatThrownBy(() -> service.addExpense(request)).isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    void kategori_dorduncuSeviyeyeInmez() {
        ExpenseCategory c1 = category(ExpenseRoot.VEHICLE, "Bakım", null);
        ExpenseCategory c2 = category(ExpenseRoot.VEHICLE, "Motor", c1.getId());
        ExpenseCategory c3 = category(ExpenseRoot.VEHICLE, "Yağ", c2.getId());
        when(categoryRepository.findById(c1.getId())).thenReturn(Optional.of(c1));
        when(categoryRepository.findById(c2.getId())).thenReturn(Optional.of(c2));
        when(categoryRepository.findById(c3.getId())).thenReturn(Optional.of(c3));
        Vehicle vehicle = vehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(accountRepository.findByVehicleId(vehicle.getId())).thenReturn(Optional.of(vehicleAccount(vehicle)));

        ExpenseRequest request = new ExpenseRequest(DAY, cash.getId(), 100L, vehicle.getId(), null, "Filtre", c3.getId(), null, null);
        assertThatThrownBy(() -> service.addExpense(request)).isInstanceOf(CategoryDepthLimitException.class);
    }

    // ------------------------------------------------------------------ duzeltme

    @Test
    void duzelt_tersYondeAyniTutardaHareketAtar() {
        LedgerEntry original = entry(LedgerEntryType.GENERAL_EXPENSE, cash, expense, 4000000L);
        when(entryRepository.lockById(original.getId())).thenReturn(Optional.of(original));
        when(entryRepository.existsByCorrectedEntryId(original.getId())).thenReturn(false);

        LedgerEntryResponse reversal = service.correct(original.getId());

        assertThat(reversal.source().id()).isEqualTo(expense.getId());
        assertThat(reversal.target().id()).isEqualTo(cash.getId());
        assertThat(reversal.amountCents()).isEqualTo(4000000L);
        assertThat(reversal.date()).isEqualTo(original.getEntryDate());
        assertThat(reversal.correctedEntryId()).isEqualTo(original.getId());
        verify(entryRepository, never()).delete(any());
    }

    @Test
    void duzeltilmisHareket_tekrarDuzeltilemez() {
        LedgerEntry original = entry(LedgerEntryType.GENERAL_EXPENSE, cash, expense, 100L);
        when(entryRepository.lockById(original.getId())).thenReturn(Optional.of(original));
        when(entryRepository.existsByCorrectedEntryId(original.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.correct(original.getId())).isInstanceOf(EntryAlreadyCorrectedException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void duzeltmeHareketi_duzeltilemez() {
        LedgerEntry reversal = entry(LedgerEntryType.GENERAL_EXPENSE, expense, cash, 100L);
        reversal.setCorrectedEntryId(UUID.randomUUID());
        when(entryRepository.lockById(reversal.getId())).thenReturn(Optional.of(reversal));

        assertThatThrownBy(() -> service.correct(reversal.getId())).isInstanceOf(CorrectionNotCorrectableException.class);
    }

    @Test
    void satisHareketi_kasaEkranindanDuzeltilemez() {
        LedgerEntry sale = entry(LedgerEntryType.VEHICLE_SALE, expense, cash, 100L);
        when(entryRepository.lockById(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.correct(sale.getId())).isInstanceOf(LinkedEntryException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void taksiteBagliTahsilat_kasaEkranindanDuzeltilemez() {
        LedgerEntry collection = entry(LedgerEntryType.COLLECTION, expense, cash, 100L);
        when(entryRepository.lockById(collection.getId())).thenReturn(Optional.of(collection));
        when(installmentRepository.existsByPaidEntryId(collection.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.correct(collection.getId())).isInstanceOf(LinkedEntryException.class);
    }

    @Test
    void planliAlisHareketi_kasaEkranindanDuzeltilemez() {
        Account sellerBalance = account(AccountType.BALANCE, tenantId);
        LedgerEntry purchase = entry(LedgerEntryType.VEHICLE_PURCHASE, sellerBalance, cash, 100L);
        when(entryRepository.lockById(purchase.getId())).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> service.correct(purchase.getId())).isInstanceOf(LinkedEntryException.class);
    }

    @Test
    void planliAlistaHesapSecimiDonmez_pesinAlistaKasaDoner() {
        Vehicle vehicle = vehicle();
        Account sellerBalance = account(AccountType.BALANCE, tenantId);
        LedgerEntry planned = entry(LedgerEntryType.VEHICLE_PURCHASE, sellerBalance, vehicleAccount(vehicle), 100L);
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of(planned));
        when(entryRepository.correctedIds(anyCollection())).thenReturn(List.of());

        assertThat(service.purchaseAccountId(vehicle.getId())).isNull();

        LedgerEntry cashPurchase = entry(LedgerEntryType.VEHICLE_PURCHASE, cash, vehicleAccount(vehicle), 100L);
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of(cashPurchase));
        assertThat(service.purchaseAccountId(vehicle.getId())).isEqualTo(cash.getId());
    }

    @Test
    void satisGelirdenOdeyeninCarisineYazilir_cariYoksaAcilir() {
        Vehicle vehicle = vehicle();
        Account income = account(AccountType.INCOME, tenantId);
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.INCOME)).thenReturn(Optional.of(income));
        Contact evim = new Contact();
        evim.setId(UUID.randomUUID());
        evim.setTenantId(tenantId);
        evim.setName("Eminevim");
        when(accountRepository.findFirstByTenantIdAndTypeAndContactId(tenantId, AccountType.BALANCE, evim.getId())).thenReturn(Optional.empty());

        LedgerEntry e = service.writeSale(vehicle, evim, 150000000L, DAY);

        assertThat(e.getType()).isEqualTo(LedgerEntryType.VEHICLE_SALE);
        assertThat(e.getSourceAccount()).isSameAs(income);
        assertThat(e.getTargetAccount().getType()).isEqualTo(AccountType.BALANCE);
        assertThat(e.getTargetAccount().getContactId()).isEqualTo(evim.getId());
        assertThat(e.getAmountCents()).isEqualTo(150000000L);
    }

    // ------------------------------------------------------------------ bakiye

    @Test
    void bakiye_girenEksiCikanHareketlerdir() {
        Account bank = account(AccountType.BANK, tenantId);
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.CASH)).thenReturn(Optional.of(cash));
        when(accountRepository.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenantId, List.of(AccountType.CASH, AccountType.BANK)))
                .thenReturn(List.of(cash, bank));
        when(entryRepository.incomingTotals(tenantId)).thenReturn(List.<Object[]>of(new Object[]{cash.getId(), 500000000L}));
        when(entryRepository.outgoingTotals(tenantId)).thenReturn(List.<Object[]>of(
                new Object[]{cash.getId(), 125000000L}, new Object[]{bank.getId(), 300000L}));

        List<AccountResponse> accounts = service.moneyAccounts();

        assertThat(accounts).extracting(AccountResponse::balanceCents).containsExactly(375000000L, -300000L);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void kasaYoksa_ilkAcilistaOlusturulur() {
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.CASH)).thenReturn(Optional.empty());

        service.moneyAccounts();

        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(created.capture());
        assertThat(created.getValue().getType()).isEqualTo(AccountType.CASH);
        assertThat(created.getValue().getTenantId()).isEqualTo(tenantId);
    }

    // ------------------------------------------------------------------ arac alisi

    @Test
    void pesinAlis_kasadanAracHesabinaYazilir() {
        Vehicle vehicle = vehicle();
        when(accountRepository.findByVehicleId(vehicle.getId())).thenReturn(Optional.empty());
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of());

        service.syncVehiclePurchase(vehicle, cash.getId());

        ArgumentCaptor<LedgerEntry> written = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(written.capture());
        assertThat(written.getValue().getType()).isEqualTo(LedgerEntryType.VEHICLE_PURCHASE);
        assertThat(written.getValue().getSourceAccount()).isSameAs(cash);
        assertThat(written.getValue().getAmountCents()).isEqualTo(72000000L);
        assertThat(written.getValue().getEntryDate()).isEqualTo(vehicle.getPurchaseDate());
    }

    @Test
    void alisHesabiSecilmediyse_deftereBirSeyYazilmaz() {
        Vehicle vehicle = vehicle();
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of());

        service.syncVehiclePurchase(vehicle, null);

        verify(entryRepository, never()).save(any());
    }

    @Test
    void alisDegismediyse_yeniHareketYazilmaz() {
        Vehicle vehicle = vehicle();
        LedgerEntry purchase = entry(LedgerEntryType.VEHICLE_PURCHASE, cash, vehicleAccount(vehicle), 72000000L);
        purchase.setEntryDate(vehicle.getPurchaseDate());
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of(purchase));
        when(entryRepository.correctedIds(anyCollection())).thenReturn(List.of());

        service.syncVehiclePurchase(vehicle, cash.getId());

        verify(entryRepository, never()).save(any());
    }

    @Test
    void alisBedeliDegisince_eskisiTerslenir_yenisiYazilir() {
        Vehicle vehicle = vehicle();
        Account vehicleAccount = vehicleAccount(vehicle);
        when(accountRepository.findByVehicleId(vehicle.getId())).thenReturn(Optional.of(vehicleAccount));
        LedgerEntry old = entry(LedgerEntryType.VEHICLE_PURCHASE, cash, vehicleAccount, 70000000L);
        old.setEntryDate(vehicle.getPurchaseDate());
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of(old));
        when(entryRepository.correctedIds(anyCollection())).thenReturn(List.of());

        service.syncVehiclePurchase(vehicle, cash.getId());   // vehicle.actualPurchaseCents = 72.000.000 kurus

        ArgumentCaptor<LedgerEntry> written = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository, times(2)).save(written.capture());
        LedgerEntry reversal = written.getAllValues().get(0);
        LedgerEntry fresh = written.getAllValues().get(1);
        assertThat(reversal.getCorrectedEntryId()).isEqualTo(old.getId());
        assertThat(reversal.getSourceAccount()).isSameAs(vehicleAccount);
        assertThat(reversal.getAmountCents()).isEqualTo(70000000L);
        assertThat(fresh.getAmountCents()).isEqualTo(72000000L);
        assertThat(fresh.getCorrectedEntryId()).isNull();
    }

    // ------------------------------------------------------------------ maliyet

    @Test
    void maliyet_alisArtiMasraflar_duzeltilenMasrafDuser_karHesaplanir() {
        Vehicle vehicle = vehicle();
        vehicle.setStatus(VehicleStatus.SOLD);
        vehicle.setSalePriceCents(80000000L);
        Account vehicleAccount = vehicleAccount(vehicle);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        LedgerEntry purchase = entry(LedgerEntryType.VEHICLE_PURCHASE, cash, vehicleAccount, 72000000L);
        LedgerEntry paint = entry(LedgerEntryType.VEHICLE_EXPENSE, cash, vehicleAccount, 500000L);
        LedgerEntry wrong = entry(LedgerEntryType.VEHICLE_EXPENSE, cash, vehicleAccount, 900000L);
        LedgerEntry wrongReversal = entry(LedgerEntryType.VEHICLE_EXPENSE, vehicleAccount, cash, 900000L);
        wrongReversal.setCorrectedEntryId(wrong.getId());
        when(entryRepository.vehicleEntries(vehicle.getId())).thenReturn(List.of(purchase, paint, wrong, wrongReversal));
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of(purchase));
        when(entryRepository.correctedIds(anyCollection())).thenReturn(List.of(wrong.getId()));

        VehicleCost cost = service.vehicleCost(vehicle.getId());

        assertThat(cost.purchaseCents()).isEqualTo(72000000L);
        assertThat(cost.purchaseInLedger()).isTrue();
        assertThat(cost.expenseCents()).isEqualTo(500000L);
        assertThat(cost.totalCents()).isEqualTo(72500000L);
        assertThat(cost.profitCents()).isEqualTo(7500000L);
        assertThat(cost.entries()).hasSize(4);
        assertThat(cost.entries().get(2).corrected()).isTrue();
    }

    @Test
    void stoktakiAracinKari_hesaplanmaz() {
        Vehicle vehicle = vehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(entryRepository.vehicleEntries(vehicle.getId())).thenReturn(List.of());
        when(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE)).thenReturn(List.of());

        VehicleCost cost = service.vehicleCost(vehicle.getId());

        assertThat(cost.totalCents()).isEqualTo(72000000L);
        assertThat(cost.purchaseInLedger()).isFalse();
        assertThat(cost.profitCents()).isNull();
    }

    // ------------------------------------------------------------------ konsinye

    @Test
    void konsinyeSatis_sahibininCarisine_satisEksiKomisyonYazilir() {
        Contact owner = contact("Burak Gules");
        Account ownerBalance = balance(owner);
        Account income = account(AccountType.INCOME, tenantId);
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.INCOME)).thenReturn(Optional.of(income));
        Vehicle v = soldConsignment(owner, 130000000L);   // 1.300.000 TL, %3
        when(entryRepository.findByVehicle_IdAndType(v.getId(), LedgerEntryType.CONSIGNMENT_SETTLEMENT)).thenReturn(List.of());

        service.syncConsignmentSettlement(v);

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        LedgerEntry e = saved.getValue();
        assertThat(e.getType()).isEqualTo(LedgerEntryType.CONSIGNMENT_SETTLEMENT);
        assertThat(e.getSourceAccount()).isSameAs(ownerBalance);   // sahibinin carisinden cikar: galeri sahibine borclu
        assertThat(e.getTargetAccount()).isSameAs(income);         // INCOME'da net komisyon kalir
        assertThat(e.getAmountCents()).isEqualTo(126100000L);      // 1.261.000 TL
        assertThat(e.getEntryDate()).isEqualTo(v.getSaleDate());
    }

    @Test
    void konsinyeBorcu_degismediyseYenidenYazilmaz_satisGeriAlininca_terslenir() {
        Contact owner = contact("Burak Gules");
        Account ownerBalance = balance(owner);
        Account income = account(AccountType.INCOME, tenantId);
        Vehicle v = soldConsignment(owner, 130000000L);
        LedgerEntry existing = entry(LedgerEntryType.CONSIGNMENT_SETTLEMENT, ownerBalance, income, 126100000L);
        existing.setEntryDate(v.getSaleDate());
        when(entryRepository.findByVehicle_IdAndType(v.getId(), LedgerEntryType.CONSIGNMENT_SETTLEMENT)).thenReturn(List.of(existing));

        service.syncConsignmentSettlement(v);
        verify(entryRepository, never()).save(any());

        v.setStatus(VehicleStatus.IN_STOCK);
        v.setSalePriceCents(null);
        v.setSaleDate(null);
        service.syncConsignmentSettlement(v);

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getCorrectedEntryId()).isEqualTo(existing.getId());
        assertThat(saved.getValue().getSourceAccount()).isSameAs(income);
        assertThat(saved.getValue().getTargetAccount()).isSameAs(ownerBalance);
    }

    @Test
    void sabitKomisyonSatistanBuyukse_reddedilir() {
        Contact owner = contact("Burak Gules");
        Vehicle v = soldConsignment(owner, 1000000L);
        v.setCommissionType(CommissionType.FIXED);
        v.setCommissionRateBp(null);
        v.setCommissionCents(2000000L);
        when(entryRepository.findByVehicle_IdAndType(v.getId(), LedgerEntryType.CONSIGNMENT_SETTLEMENT)).thenReturn(List.of());

        assertThatThrownBy(() -> service.syncConsignmentSettlement(v)).isInstanceOf(CommissionExceedsSaleException.class);
    }

    @Test
    void konsinyeMasrafi_sahibineYansitilirsa_sahibininCarisine_yoksaGenelGidereYazilir() {
        Contact owner = contact("Burak Gules");
        Account ownerBalance = balance(owner);
        Vehicle v = soldConsignment(owner, 130000000L);
        v.setStatus(VehicleStatus.IN_STOCK);
        when(vehicleRepository.findById(v.getId())).thenReturn(Optional.of(v));
        ExpenseCategory paint = category(ExpenseRoot.VEHICLE, "Boya", null);
        when(categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(tenantId, ExpenseRoot.VEHICLE, "Boya"))
                .thenReturn(Optional.of(paint));

        service.addExpense(new ExpenseRequest(DAY, cash.getId(), 500000L, v.getId(), null, "Boya", null, null, true));
        service.addExpense(new ExpenseRequest(DAY, cash.getId(), 300000L, v.getId(), null, "Boya", null, null, false));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository, times(2)).save(saved.capture());
        LedgerEntry charged = saved.getAllValues().get(0);
        LedgerEntry absorbed = saved.getAllValues().get(1);
        assertThat(charged.getSourceAccount()).isSameAs(cash);
        assertThat(charged.getTargetAccount()).isSameAs(ownerBalance);   // sahibi galeriye borclanir
        assertThat(charged.getVehicle()).isSameAs(v);
        assertThat(absorbed.getTargetAccount()).isSameAs(expense);       // galerinin genel gideri
        assertThat(absorbed.getVehicle()).isSameAs(v);
        verify(accountRepository, never()).findByVehicleId(v.getId());   // konsinye araca maliyet hesabi acilmaz
    }

    @Test
    void konsinyeMaliyeti_alisYok_kar_komisyonEksiUstlenilenMasraf() {
        Contact owner = contact("Burak Gules");
        Account ownerBalance = balance(owner);
        Vehicle v = soldConsignment(owner, 130000000L);
        when(vehicleRepository.findById(v.getId())).thenReturn(Optional.of(v));
        when(entryRepository.vehicleEntries(v.getId())).thenReturn(List.of(
                entry(LedgerEntryType.VEHICLE_EXPENSE, cash, ownerBalance, 500000L),
                entry(LedgerEntryType.VEHICLE_EXPENSE, cash, expense, 300000L)));

        VehicleCost cost = service.vehicleCost(v.getId());

        assertThat(cost.purchaseCents()).isZero();
        assertThat(cost.expenseCents()).isEqualTo(300000L);
        assertThat(cost.ownerChargedCents()).isEqualTo(500000L);
        assertThat(cost.commissionCents()).isEqualTo(3900000L);
        assertThat(cost.ownerPayoutCents()).isEqualTo(126100000L);
        assertThat(cost.profitCents()).isEqualTo(3600000L);   // 39.000 - 3.000
    }

    @Test
    void ortakAracMaliyeti_sahiplerOrtakFinansmandan_karAracinTamKari() {
        Vehicle v = vehicle();
        v.setOwnership(Ownership.CO_OWNED);
        v.setStatus(VehicleStatus.SOLD);
        v.setSalePriceCents(72000001L);   // kar 0,01 TL
        when(vehicleRepository.findById(v.getId())).thenReturn(Optional.of(v));
        when(entryRepository.vehicleEntries(v.getId())).thenReturn(List.of());
        List<ShareResponse> owners = List.of(new ShareResponse(null, "Havuz", 5000, 5000, ContributionSource.CAPITAL, null, 0, 1L, 1L));
        when(coOwnershipService.owners(v)).thenReturn(owners);

        VehicleCost cost = service.vehicleCost(v.getId());

        assertThat(cost.profitCents()).isEqualTo(1L);
        assertThat(cost.shares()).isEqualTo(owners);
    }

    @Test
    void ortakKarPayiVeCeptenKatki_kasadanDuzeltilemez() {
        LedgerEntry share = new LedgerEntry();
        share.setId(UUID.randomUUID());
        share.setTenantId(tenantId);
        share.setType(LedgerEntryType.VEHICLE_PROFIT_SHARE);
        when(entryRepository.lockById(share.getId())).thenReturn(Optional.of(share));
        when(coOwnershipService.isLinkedEntry(share)).thenReturn(true);

        assertThatThrownBy(() -> service.correct(share.getId())).isInstanceOf(LinkedEntryException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void kisiyeOdeme_kasadanCariyeYazilir() {
        Contact owner = contact("Burak Gules");
        Account ownerBalance = balance(owner);
        when(contactRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        LedgerEntryResponse res = service.payContact(owner.getId(), new ContactPaymentRequest(cash.getId(), DAY, 50000000L, null));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(LedgerEntryType.PAYMENT);
        assertThat(saved.getValue().getSourceAccount()).isSameAs(cash);
        assertThat(saved.getValue().getTargetAccount()).isSameAs(ownerBalance);
        assertThat(res.description()).isEqualTo("Ödeme");
    }

    @Test
    void konsinyeBorcHareketi_kasaEkranindanDuzeltilemez() {
        Account ownerBalance = account(AccountType.BALANCE, tenantId);
        LedgerEntry e = entry(LedgerEntryType.CONSIGNMENT_SETTLEMENT, ownerBalance, account(AccountType.INCOME, tenantId), 100L);
        when(entryRepository.lockById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.correct(e.getId())).isInstanceOf(LinkedEntryException.class);
    }

    // ------------------------------------------------------------------ masraf duzenle / sil

    /** Hareketlerin bir hesaba etkisi: giren arti, cikan eksi. */
    private static long effectOn(List<LedgerEntry> entries, Account account) {
        long sum = 0;
        for (LedgerEntry e : entries) {
            if (e.getTargetAccount() == account) sum += e.getAmountCents();
            if (e.getSourceAccount() == account) sum -= e.getAmountCents();
        }
        return sum;
    }

    @Test
    void masrafDuzenleme_eskiTerslenirYeniYazilir_maliyetVeKasaDogruDegisir() {
        Vehicle vehicle = vehicle();
        Account vehicleAcc = vehicleAccount(vehicle);
        Account bank = account(AccountType.BANK, tenantId);
        when(accountRepository.findById(bank.getId())).thenReturn(Optional.of(bank));
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(accountRepository.findByVehicleId(vehicle.getId())).thenReturn(Optional.of(vehicleAcc));
        when(categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(tenantId, ExpenseRoot.VEHICLE, "Boya"))
                .thenReturn(Optional.of(category(ExpenseRoot.VEHICLE, "Boya", null)));
        LedgerEntry original = entry(LedgerEntryType.VEHICLE_EXPENSE, cash, vehicleAcc, 500000L);
        original.setVehicle(vehicle);
        when(entryRepository.lockById(original.getId())).thenReturn(Optional.of(original));

        // 5.000 TL kasadan -> 3.000 TL bankadan
        service.updateExpense(original.getId(), expenseRequest(bank.getId(), 300000L, vehicle.getId(), "Boya"));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository, times(2)).save(saved.capture());
        List<LedgerEntry> written = saved.getAllValues();
        assertThat(written.get(0).getCorrectedEntryId()).isEqualTo(original.getId());   // once ters kayit
        assertThat(written.get(1).getCorrectedEntryId()).isNull();                      // sonra yeni masraf
        List<LedgerEntry> all = new java.util.ArrayList<>(written);
        all.add(original);
        assertThat(effectOn(all, vehicleAcc)).isEqualTo(300000L);   // aracin maliyetinde artik 3.000
        assertThat(effectOn(all, cash)).isZero();                   // kasaya 5.000 geri dondu
        assertThat(effectOn(all, bank)).isEqualTo(-300000L);        // bankadan 3.000 cikti
    }

    @Test
    void masrafSilme_tersKayitla_maliyetVeKasaGeriAlinir_ikinciSilmeReddedilir() {
        Vehicle vehicle = vehicle();
        Account vehicleAcc = vehicleAccount(vehicle);
        LedgerEntry original = entry(LedgerEntryType.VEHICLE_EXPENSE, cash, vehicleAcc, 500000L);
        when(entryRepository.lockById(original.getId())).thenReturn(Optional.of(original));

        service.deleteExpense(original.getId());

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        LedgerEntry reversal = saved.getValue();
        assertThat(reversal.getCorrectedEntryId()).isEqualTo(original.getId());
        assertThat(effectOn(List.of(original, reversal), vehicleAcc)).isZero();
        assertThat(effectOn(List.of(original, reversal), cash)).isZero();

        when(entryRepository.existsByCorrectedEntryId(original.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteExpense(original.getId())).isInstanceOf(EntryAlreadyCorrectedException.class);
        assertThatThrownBy(() -> service.updateExpense(original.getId(), expenseRequest(cash.getId(), 1L, null, "Kira")))
                .isInstanceOf(EntryAlreadyCorrectedException.class);
    }

    @Test
    void masrafOlmayanHareket_duzenlenipSilinemez() {
        LedgerEntry sale = entry(LedgerEntryType.VEHICLE_SALE, account(AccountType.INCOME, tenantId), account(AccountType.BALANCE, tenantId), 100L);
        when(entryRepository.lockById(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.deleteExpense(sale.getId())).isInstanceOf(NotAnExpenseException.class);
        verify(entryRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ kasa islemleri

    @Test
    void virman_toplamNakdiDegistirmez() {
        Account bank = account(AccountType.BANK, tenantId);
        when(accountRepository.findById(bank.getId())).thenReturn(Optional.of(bank));

        service.transfer(new CapitalRequests.Transfer(cash.getId(), bank.getId(), DAY, 2500000L, null));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        LedgerEntry e = saved.getValue();
        assertThat(e.getType()).isEqualTo(LedgerEntryType.TRANSFER);
        assertThat(e.getSourceAccount()).isSameAs(cash);
        assertThat(e.getTargetAccount()).isSameAs(bank);
        long moneyEffect = (e.getTargetAccount().getType().isMoneyAccount() ? e.getAmountCents() : 0)
                - (e.getSourceAccount().getType().isMoneyAccount() ? e.getAmountCents() : 0);
        assertThat(moneyEffect).isZero();   // kasa -25.000, banka +25.000
        assertThat(e.getDescription()).isEqualTo("Virman");
    }

    @Test
    void virman_ayniHesabaYaDaParaHesabiDisinaYapilamaz() {
        assertThatThrownBy(() -> service.transfer(new CapitalRequests.Transfer(cash.getId(), cash.getId(), DAY, 1L, null)))
                .isInstanceOf(SameAccountException.class);
        when(accountRepository.findById(expense.getId())).thenReturn(Optional.of(expense));
        assertThatThrownBy(() -> service.transfer(new CapitalRequests.Transfer(cash.getId(), expense.getId(), DAY, 1L, null)))
                .isInstanceOf(InvalidAccountException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void acilisBakiyesi_hesapBasinaBirKezGirilir_duzeltilirseYenidenGirilebilir() {
        Account opening = account(AccountType.OPENING, tenantId);
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.OPENING)).thenReturn(Optional.of(opening));
        when(entryRepository.byTypeTouching(LedgerEntryType.OPENING_BALANCE, cash.getId())).thenReturn(List.of());

        service.openingBalance(new CapitalRequests.Opening(cash.getId(), null, null, DAY, 12000000L));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        LedgerEntry first = saved.getValue();
        assertThat(first.getSourceAccount()).isSameAs(opening);
        assertThat(first.getTargetAccount()).isSameAs(cash);

        // Ikinci giris reddedilir
        when(entryRepository.byTypeTouching(LedgerEntryType.OPENING_BALANCE, cash.getId())).thenReturn(List.of(first));
        when(entryRepository.correctedIds(anyCollection())).thenReturn(List.of());
        assertThatThrownBy(() -> service.openingBalance(new CapitalRequests.Opening(cash.getId(), null, null, DAY, 1L)))
                .isInstanceOf(OpeningBalanceExistsException.class);

        // Kasa'dan duzeltildiyse yeniden girilebilir
        when(entryRepository.correctedIds(anyCollection())).thenReturn(List.of(first.getId()));
        service.openingBalance(new CapitalRequests.Opening(cash.getId(), null, null, DAY, 11000000L));
        verify(entryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void cariAcilisBakiyesi_borcumuzsaCaridenAcilisaYazilir() {
        Contact seller = contact("Mehmet Yilmaz");
        Account sellerBalance = balance(seller);
        when(contactRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        Account opening = account(AccountType.OPENING, tenantId);
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.OPENING)).thenReturn(Optional.of(opening));
        when(entryRepository.byTypeTouching(LedgerEntryType.OPENING_BALANCE, sellerBalance.getId())).thenReturn(List.of());

        service.openingBalance(new CapitalRequests.Opening(null, seller.getId(), CapitalRequests.OpeningSide.PAYABLE, DAY, 500000L));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceAccount()).isSameAs(sellerBalance);   // cari eksiye: biz borcluyuz
        assertThat(saved.getValue().getTargetAccount()).isSameAs(opening);
    }

    @Test
    void acilisBakiyesinde_hesapVeKisiBirlikteYaDaHicVerilmezse_reddedilir() {
        assertThatThrownBy(() -> service.openingBalance(new CapitalRequests.Opening(null, null, null, DAY, 1L)))
                .isInstanceOf(InvalidAccountException.class);
        assertThatThrownBy(() -> service.openingBalance(new CapitalRequests.Opening(cash.getId(), UUID.randomUUID(), null, DAY, 1L)))
                .isInstanceOf(InvalidAccountException.class);
    }

    // ------------------------------------------------------------------ yardimcilar

    private Contact contact(String name) {
        Contact c = new Contact();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setName(name);
        return c;
    }

    private Account balance(Contact c) {
        Account a = account(AccountType.BALANCE, tenantId);
        a.setContactId(c.getId());
        when(accountRepository.findFirstByTenantIdAndTypeAndContactId(tenantId, AccountType.BALANCE, c.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private Vehicle soldConsignment(Contact owner, long salePrice) {
        Vehicle v = vehicle();
        v.setOwnership(Ownership.CONSIGNMENT);
        v.setActualPurchaseCents(null);
        v.setConsignor(owner);
        v.setCommissionType(CommissionType.RATE);
        v.setCommissionRateBp(300);
        v.setStatus(VehicleStatus.SOLD);
        v.setSaleDate(LocalDate.of(2026, 6, 20));
        v.setSalePriceCents(salePrice);
        return v;
    }

    private ExpenseRequest expenseRequest(UUID sourceId, long amount, UUID vehicleId, String categoryName) {
        return new ExpenseRequest(DAY, sourceId, amount, vehicleId, null, categoryName, null, null, null);
    }

    private Account account(AccountType type, UUID tenant) {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenant);
        a.setType(type);
        a.setName(type.name());
        return a;
    }

    private Account vehicleAccount(Vehicle vehicle) {
        Account a = account(AccountType.VEHICLE, vehicle.getTenantId());
        a.setVehicleId(vehicle.getId());
        return a;
    }

    private Vehicle vehicle() {
        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID());
        v.setTenantId(tenantId);
        v.setPlate("34 KLM 4521");
        v.setMake("Renault");
        v.setModel("Clio");
        v.setPurchaseDate(LocalDate.of(2026, 6, 1));
        v.setActualPurchaseCents(72000000L);
        return v;
    }

    private ExpenseCategory category(ExpenseRoot root, String name, UUID parentId) {
        ExpenseCategory c = new ExpenseCategory();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setRoot(root);
        c.setName(name);
        c.setParentId(parentId);
        return c;
    }

    private LedgerEntry entry(LedgerEntryType type, Account source, Account target, long amount) {
        LedgerEntry e = new LedgerEntry();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setType(type);
        e.setSourceAccount(source);
        e.setTargetAccount(target);
        e.setAmountCents(amount);
        e.setEntryDate(DAY);
        return e;
    }
}
