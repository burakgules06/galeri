package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CategoryDepthLimitException;
import com.galeri.app.exception.runtime.CorrectionNotCorrectableException;
import com.galeri.app.exception.runtime.EntryAlreadyCorrectedException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidCategoryException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.ExpenseCategory;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ExpenseCategoryRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehicleRepository;
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

    private static final LocalDate DAY = LocalDate.of(2026, 7, 1);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private LedgerServiceImpl service;

    private Account cash;
    private Account expense;

    @BeforeEach
    void setUp() {
        service = new LedgerServiceImpl(accountRepository, entryRepository, categoryRepository, vehicleRepository, panelUserRepository);
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

        ExpenseRequest request = new ExpenseRequest(DAY, cash.getId(), 100L, vehicle.getId(), rent.getId(), null, null, null);
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

        ExpenseRequest request = new ExpenseRequest(DAY, cash.getId(), 100L, vehicle.getId(), null, "Filtre", c3.getId(), null);
        assertThatThrownBy(() -> service.addExpense(request)).isInstanceOf(CategoryDepthLimitException.class);
    }

    // ------------------------------------------------------------------ duzeltme

    @Test
    void duzelt_tersYondeAyniTutardaHareketAtar() {
        LedgerEntry original = entry(LedgerEntryType.GENERAL_EXPENSE, cash, expense, 4000000L);
        when(entryRepository.findById(original.getId())).thenReturn(Optional.of(original));
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
        when(entryRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(entryRepository.existsByCorrectedEntryId(original.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.correct(original.getId())).isInstanceOf(EntryAlreadyCorrectedException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void duzeltmeHareketi_duzeltilemez() {
        LedgerEntry reversal = entry(LedgerEntryType.GENERAL_EXPENSE, expense, cash, 100L);
        reversal.setCorrectedEntryId(UUID.randomUUID());
        when(entryRepository.findById(reversal.getId())).thenReturn(Optional.of(reversal));

        assertThatThrownBy(() -> service.correct(reversal.getId())).isInstanceOf(CorrectionNotCorrectableException.class);
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

    // ------------------------------------------------------------------ yardimcilar

    private ExpenseRequest expenseRequest(UUID sourceId, long amount, UUID vehicleId, String categoryName) {
        return new ExpenseRequest(DAY, sourceId, amount, vehicleId, null, categoryName, null, null);
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
