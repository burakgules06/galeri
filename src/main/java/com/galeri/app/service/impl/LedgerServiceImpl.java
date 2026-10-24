package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CategoryDepthLimitException;
import com.galeri.app.exception.runtime.CorrectionNotCorrectableException;
import com.galeri.app.exception.runtime.EntryAlreadyCorrectedException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidCategoryException;
import com.galeri.app.exception.runtime.LinkedEntryException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.ExpenseCategory;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.model.response.AccountSummary;
import com.galeri.app.model.response.CategoryResponse;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.model.response.VehicleRef;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ExpenseCategoryRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    static final int LIST_LIMIT = 300;
    static final int CATEGORY_MAX_DEPTH = 3;
    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final VehicleRepository vehicleRepository;
    private final PanelUserRepository panelUserRepository;
    private final InstallmentRepository installmentRepository;

    // ------------------------------------------------------------------ hesaplar

    @Override
    @Transactional
    public List<AccountResponse> moneyAccounts() {
        UUID tenantId = CurrentUser.tenantId();
        systemAccount(tenantId, AccountType.CASH);
        Map<UUID, Long> balances = balances(tenantId);
        return accountRepository.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenantId, List.of(AccountType.CASH, AccountType.BANK))
                .stream()
                .sorted(Comparator.comparing(Account::getType))   // once kasa, sonra bankalar (eklenme sirasiyla)
                .map(a -> new AccountResponse(a.getId(), a.getType(), a.getName(), balances.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public AccountResponse addBank(AccountRequest request) {
        Account a = new Account();
        a.setTenantId(CurrentUser.tenantId());
        a.setType(AccountType.BANK);
        a.setName(request.name().trim());
        Account saved = accountRepository.save(a);
        return new AccountResponse(saved.getId(), saved.getType(), saved.getName(), 0L);
    }

    // ------------------------------------------------------------------ hareketler

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> entries(UUID accountId) {
        UUID tenantId = CurrentUser.tenantId();
        PageRequest page = PageRequest.of(0, LIST_LIMIT);
        List<LedgerEntry> list;
        if (accountId == null) {
            list = entryRepository.list(tenantId, page);
        } else {
            getAccount(accountId);
            list = entryRepository.accountEntries(tenantId, accountId, page);
        }
        return toResponse(list);
    }

    @Override
    @Transactional
    public LedgerEntryResponse addExpense(ExpenseRequest r) {
        UUID tenantId = CurrentUser.tenantId();
        Account source = getMoneyAccount(r.sourceAccountId());

        Vehicle vehicle = null;
        Account target;
        ExpenseRoot root;
        LedgerEntryType type;
        if (r.vehicleId() != null) {
            vehicle = getVehicle(r.vehicleId());
            target = vehicleAccount(vehicle);
            root = ExpenseRoot.VEHICLE;
            type = LedgerEntryType.VEHICLE_EXPENSE;
        } else {
            target = systemAccount(tenantId, AccountType.EXPENSE);
            root = ExpenseRoot.GENERAL;
            type = LedgerEntryType.GENERAL_EXPENSE;
        }
        ExpenseCategory category = findOrCreateCategory(tenantId, root, r.categoryId(), r.categoryName(), r.parentCategoryId());

        LedgerEntry e = newEntry(tenantId, type, source, target, r.amountCents(), r.date());
        e.setVehicle(vehicle);
        e.setCategory(category);
        e.setDescription(blankToNull(r.description()));
        return toResponse(entryRepository.save(e), false);
    }

    @Override
    @Transactional
    public LedgerEntryResponse correct(UUID entryId) {
        LedgerEntry original = reversible(entryId);
        // Satis ve taksit tahsilati plana bagli: kasa ekranindan terslenirse plan ile defter ayrisir.
        if (original.getType() == LedgerEntryType.VEHICLE_SALE || installmentRepository.existsByPaidEntryId(original.getId())) {
            throw new LinkedEntryException();
        }
        return toResponse(writeReversal(original), false);
    }

    // ------------------------------------------------------------------ odeme plani

    @Override
    @Transactional
    public LedgerEntry writeSale(Vehicle vehicle, Contact payer, long amountCents, LocalDate date) {
        Account income = systemAccount(vehicle.getTenantId(), AccountType.INCOME);
        LedgerEntry e = newEntry(vehicle.getTenantId(), LedgerEntryType.VEHICLE_SALE, income, balanceAccount(payer), amountCents, date);
        e.setVehicle(vehicle);
        e.setDescription("Satış");
        return entryRepository.save(e);
    }

    @Override
    @Transactional
    public LedgerEntry writeSettlement(PlanDirection direction, Vehicle vehicle, Contact payer, UUID accountId,
                                       long amountCents, LocalDate date, String description) {
        Account money = getMoneyAccount(accountId);
        Account balance = balanceAccount(payer);
        LedgerEntry e = direction == PlanDirection.SALE
                ? newEntry(vehicle.getTenantId(), LedgerEntryType.COLLECTION, balance, money, amountCents, date)
                : newEntry(vehicle.getTenantId(), LedgerEntryType.PAYMENT, money, balance, amountCents, date);
        e.setVehicle(vehicle);
        e.setDescription(description);
        return entryRepository.save(e);
    }

    @Override
    @Transactional
    public void reverse(UUID entryId) {
        writeReversal(reversible(entryId));
    }

    @Override
    @Transactional
    public void reverseVehicleSales(UUID vehicleId) {
        List<LedgerEntry> sales = entryRepository.findByVehicle_IdAndType(vehicleId, LedgerEntryType.VEHICLE_SALE).stream()
                .filter(e -> e.getCorrectedEntryId() == null)
                .toList();
        if (sales.isEmpty()) return;
        Set<UUID> corrected = new HashSet<>(entryRepository.correctedIds(sales.stream().map(LedgerEntry::getId).toList()));
        sales.stream().filter(e -> !corrected.contains(e.getId())).forEach(this::writeReversal);
    }

    /** Tersi yazilabilecek hareket: bu galerinin, kendisi duzeltme degil, daha once duzeltilmemis. */
    private LedgerEntry reversible(UUID entryId) {
        LedgerEntry original = entryRepository.findById(entryId).orElseThrow(RecordNotFoundException::new);
        if (!original.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (original.getCorrectedEntryId() != null) throw new CorrectionNotCorrectableException();
        if (entryRepository.existsByCorrectedEntryId(original.getId())) throw new EntryAlreadyCorrectedException();
        return original;
    }

    /** Kisinin carisi (BALANCE); yoksa ilk ihtiyacta acilir. */
    private Account balanceAccount(Contact contact) {
        return accountRepository.findFirstByTenantIdAndTypeAndContactId(contact.getTenantId(), AccountType.BALANCE, contact.getId())
                .orElseGet(() -> {
                    Account a = new Account();
                    a.setTenantId(contact.getTenantId());
                    a.setType(AccountType.BALANCE);
                    a.setContactId(contact.getId());
                    a.setName(contact.getName());
                    return accountRepository.save(a);
                });
    }

    // ------------------------------------------------------------------ kategoriler

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories(ExpenseRoot root) {
        UUID tenantId = CurrentUser.tenantId();
        Map<UUID, Long> usage = new HashMap<>();
        for (Object[] row : entryRepository.categoryUsage(tenantId)) {
            usage.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return categoryRepository.findByTenantIdAndRootAndArchivedFalse(tenantId, root).stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getRoot(), c.getParentId(), usage.getOrDefault(c.getId(), 0L)))
                .sorted(Comparator.comparingLong(CategoryResponse::usage).reversed()
                        .thenComparing(CategoryResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ------------------------------------------------------------------ arac

    @Override
    @Transactional(readOnly = true)
    public VehicleCost vehicleCost(UUID vehicleId) {
        Vehicle vehicle = getVehicle(vehicleId);
        List<LedgerEntry> entries = entryRepository.vehicleEntries(vehicle.getId());

        // Duzeltmeler ters yonde oldugu icin isaretli toplamda kendiliginden duser.
        long expense = 0;
        for (LedgerEntry e : entries) {
            if (e.getType() != LedgerEntryType.VEHICLE_EXPENSE) continue;
            expense += e.getTargetAccount().getType() == AccountType.VEHICLE ? e.getAmountCents() : -e.getAmountCents();
        }
        boolean purchaseInLedger = activePurchase(vehicle.getId()) != null;

        // Alis her zaman aracin gercek alis bedelinden: alis deftere yazilmamis olsa da maliyet dogru kalir.
        long purchase = vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents();
        long total = purchase + expense;
        Long sale = vehicle.getSalePriceCents();
        Long profit = vehicle.getStatus() != VehicleStatus.IN_STOCK && sale != null ? sale - total : null;
        return new VehicleCost(vehicle.getId(), purchase, purchaseInLedger, expense, total, sale, profit, toResponse(entries));
    }

    @Override
    @Transactional
    public void syncVehiclePurchase(Vehicle vehicle, UUID purchaseAccountId) {
        long amount = vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents();
        boolean shouldWrite = purchaseAccountId != null && amount > 0;
        LedgerEntry active = activePurchase(vehicle.getId());

        if (active != null && shouldWrite
                && active.getSourceAccount().getId().equals(purchaseAccountId)
                && active.getAmountCents() == amount
                && active.getEntryDate().equals(vehicle.getPurchaseDate())) {
            return;   // degisiklik yok
        }
        if (active == null && !shouldWrite) return;

        Account source = shouldWrite ? getMoneyAccount(purchaseAccountId) : null;
        if (active != null) writeReversal(active);
        if (shouldWrite) {
            LedgerEntry e = newEntry(vehicle.getTenantId(), LedgerEntryType.VEHICLE_PURCHASE, source, vehicleAccount(vehicle),
                    amount, vehicle.getPurchaseDate());
            e.setVehicle(vehicle);
            e.setDescription("Araç alışı");
            entryRepository.save(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UUID purchaseAccountId(UUID vehicleId) {
        if (vehicleId == null) return null;
        LedgerEntry active = activePurchase(vehicleId);
        return active == null ? null : active.getSourceAccount().getId();
    }

    // ------------------------------------------------------------------ yardimcilar

    /** Duzeltilmemis tek VEHICLE_PURCHASE hareketi (duzeltme hareketleri haric). */
    private LedgerEntry activePurchase(UUID vehicleId) {
        List<LedgerEntry> purchases = entryRepository.findByVehicle_IdAndType(vehicleId, LedgerEntryType.VEHICLE_PURCHASE).stream()
                .filter(e -> e.getCorrectedEntryId() == null)
                .toList();
        if (purchases.isEmpty()) return null;
        Set<UUID> corrected = new HashSet<>(entryRepository.correctedIds(purchases.stream().map(LedgerEntry::getId).toList()));
        return purchases.stream().filter(e -> !corrected.contains(e.getId())).findFirst().orElse(null);
    }

    private LedgerEntry writeReversal(LedgerEntry original) {
        LedgerEntry reversal = newEntry(original.getTenantId(), original.getType(), original.getTargetAccount(),
                original.getSourceAccount(), original.getAmountCents(), original.getEntryDate());   // ayni tarih: donem toplamlari sifirlanir
        reversal.setVehicle(original.getVehicle());
        reversal.setCategory(original.getCategory());
        reversal.setDescription("Düzeltme" + (original.getDescription() == null ? "" : ": " + original.getDescription()));
        reversal.setCorrectedEntryId(original.getId());
        return entryRepository.save(reversal);
    }

    private LedgerEntry newEntry(UUID tenantId, LedgerEntryType type, Account source, Account target, long amount, LocalDate date) {
        LedgerEntry e = new LedgerEntry();
        e.setTenantId(tenantId);
        e.setType(type);
        e.setSourceAccount(source);
        e.setTargetAccount(target);
        e.setAmountCents(amount);
        e.setEntryDate(date);
        e.setCreatedBy(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
        return e;
    }

    private Map<UUID, Long> balances(UUID tenantId) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] s : entryRepository.incomingTotals(tenantId)) result.merge((UUID) s[0], ((Number) s[1]).longValue(), Long::sum);
        for (Object[] s : entryRepository.outgoingTotals(tenantId)) result.merge((UUID) s[0], -((Number) s[1]).longValue(), Long::sum);
        return result;
    }

    private Account systemAccount(UUID tenantId, AccountType type) {
        return accountRepository.findFirstByTenantIdAndType(tenantId, type).orElseGet(() -> {
            Account a = new Account();
            a.setTenantId(tenantId);
            a.setType(type);
            a.setName(type == AccountType.CASH ? "Kasa" : type == AccountType.EXPENSE ? "Genel giderler" : "Gelirler");
            return accountRepository.save(a);
        });
    }

    private Account vehicleAccount(Vehicle vehicle) {
        return accountRepository.findByVehicleId(vehicle.getId()).orElseGet(() -> {
            Account a = new Account();
            a.setTenantId(vehicle.getTenantId());
            a.setType(AccountType.VEHICLE);
            a.setVehicleId(vehicle.getId());
            a.setName("Araç " + vehicle.getPlate());
            return accountRepository.save(a);
        });
    }

    private Account getAccount(UUID id) {
        Account a = accountRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!a.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return a;
    }

    private Account getMoneyAccount(UUID id) {
        Account a = getAccount(id);
        if (!a.getType().isMoneyAccount()) throw new InvalidAccountException();
        return a;
    }

    private Vehicle getVehicle(UUID id) {
        Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!vehicle.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return vehicle;
    }

    private ExpenseCategory findOrCreateCategory(UUID tenantId, ExpenseRoot root, UUID categoryId, String name, UUID parentId) {
        if (categoryId != null) return getCategory(categoryId, tenantId, root);

        String clean = blankToNull(name);
        if (clean == null) throw new InvalidCategoryException();
        ExpenseCategory parent = parentId == null ? null : getCategory(parentId, tenantId, root);
        if (parent != null && depth(parent) >= CATEGORY_MAX_DEPTH) throw new CategoryDepthLimitException();

        return (parent == null
                ? categoryRepository.findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(tenantId, root, clean)
                : categoryRepository.findFirstByTenantIdAndRootAndParentIdAndNameIgnoreCase(tenantId, root, parent.getId(), clean))
                .orElseGet(() -> {
                    ExpenseCategory c = new ExpenseCategory();
                    c.setTenantId(tenantId);
                    c.setRoot(root);
                    c.setParentId(parent == null ? null : parent.getId());
                    c.setName(capitalize(clean));
                    return categoryRepository.save(c);
                });
    }

    private ExpenseCategory getCategory(UUID id, UUID tenantId, ExpenseRoot root) {
        ExpenseCategory c = categoryRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!c.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        if (c.getRoot() != root) throw new InvalidCategoryException();
        return c;
    }

    /** Kok seviyedeki kategori 1. */
    private int depth(ExpenseCategory c) {
        int depth = 1;
        UUID parentId = c.getParentId();
        while (parentId != null && depth <= CATEGORY_MAX_DEPTH) {
            depth++;
            parentId = categoryRepository.findById(parentId).map(ExpenseCategory::getParentId).orElse(null);
        }
        return depth;
    }

    private List<LedgerEntryResponse> toResponse(List<LedgerEntry> list) {
        if (list.isEmpty()) return List.of();
        Set<UUID> corrected = new HashSet<>(entryRepository.correctedIds(list.stream().map(LedgerEntry::getId).toList()));
        return list.stream().map(e -> toResponse(e, corrected.contains(e.getId()))).toList();
    }

    private static LedgerEntryResponse toResponse(LedgerEntry e, boolean corrected) {
        Vehicle v = e.getVehicle();
        return new LedgerEntryResponse(e.getId(), e.getEntryDate(), e.getType(),
                accountSummary(e.getSourceAccount()), accountSummary(e.getTargetAccount()), e.getAmountCents(),
                v == null ? null : new VehicleRef(v.getId(), v.getPlate(), v.getMake(), v.getModel()),
                e.getCategory() == null ? null : e.getCategory().getName(),
                e.getDescription(), e.getCorrectedEntryId(), corrected,
                e.getCreatedBy() == null ? null : e.getCreatedBy().getFullName(),
                e.getCreatedAt());
    }

    private static AccountSummary accountSummary(Account a) {
        return new AccountSummary(a.getId(), a.getType(), a.getName());
    }

    /** "kaporta" -> "Kaporta", "ışık" -> "Işık" (Turkce buyuk harf). */
    static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(TURKISH) + s.substring(1);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
