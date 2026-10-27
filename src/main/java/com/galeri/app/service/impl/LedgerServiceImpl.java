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
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SameAccountException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.ExpenseCategory;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ContactPaymentRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.model.response.AccountSummary;
import com.galeri.app.model.response.CategoryResponse;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.ShareResponse;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.model.response.VehicleRef;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.ExpenseCategoryRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.PaymentPlanRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.util.ConsignmentMath;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private final ContactRepository contactRepository;
    private final CoOwnershipService coOwnershipService;
    private final PaymentPlanRepository planRepository;

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
        return toResponse(afterExpense(writeExpense(r)), false);
    }

    @Override
    @Transactional
    public List<LedgerEntryResponse> addExpenses(List<ExpenseRequest> requests) {
        List<LedgerEntryResponse> result = new ArrayList<>(requests.size());
        for (ExpenseRequest r : requests) result.add(toResponse(afterExpense(writeExpense(r)), false));
        return result;
    }

    @Override
    @Transactional
    public LedgerEntryResponse updateExpense(UUID entryId, ExpenseRequest r) {
        // Defter degismez: eski masraf terslenir, yeni degerlerle yeni masraf yazilir (ayni transaction)
        afterExpense(writeReversal(changeableExpense(entryId)));
        return toResponse(afterExpense(writeExpense(r)), false);
    }

    @Override
    @Transactional
    public LedgerEntryResponse deleteExpense(UUID entryId) {
        // Silme = ters kayit: para kasaya / bankaya geri doner, arac maliyetinden duser
        return toResponse(afterExpense(writeReversal(changeableExpense(entryId))), false);
    }

    /** Satilmis ortak araca masraf girildi / degisti: kar degisti, ortaklarin kar paylari yeniden yazilir. */
    private LedgerEntry afterExpense(LedgerEntry e) {
        Vehicle v = e.getVehicle();
        if (v != null && v.getOwnership() == Ownership.CO_OWNED && v.getStatus() != VehicleStatus.IN_STOCK) {
            entryRepository.flush();
            coOwnershipService.resyncProfitShares(v);
        }
        return e;
    }

    /** Duzenlenip silinebilecek masraf: bu galerinin, gecerli (duzeltme degil, duzeltilmemis) arac masrafi ya da genel gideri. */
    private LedgerEntry changeableExpense(UUID entryId) {
        LedgerEntry original = reversible(entryId);
        if (original.getType() != LedgerEntryType.VEHICLE_EXPENSE && original.getType() != LedgerEntryType.GENERAL_EXPENSE) {
            throw new NotAnExpenseException();
        }
        return original;
    }

    private LedgerEntry writeExpense(ExpenseRequest r) {
        UUID tenantId = CurrentUser.tenantId();
        Account source = getMoneyAccount(r.sourceAccountId());

        Vehicle vehicle = null;
        Account target;
        ExpenseRoot root;
        LedgerEntryType type;
        if (r.vehicleId() != null) {
            vehicle = getVehicle(r.vehicleId());
            root = ExpenseRoot.VEHICLE;
            type = LedgerEntryType.VEHICLE_EXPENSE;
            // Konsinye aracin maliyet hesabi yok: masraf ya sahibine yansitilir (sahibi galeriye borclanir)
            // ya da galerinin genel gideridir. Arac bagi her iki durumda da kalir (arac karinda gorunur).
            if (vehicle.getOwnership() == Ownership.CONSIGNMENT) {
                target = Boolean.TRUE.equals(r.chargeToOwner())
                        ? balanceAccount(vehicle.getConsignor())
                        : systemAccount(tenantId, AccountType.EXPENSE);
            } else {
                target = vehicleAccount(vehicle);
            }
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
        return entryRepository.save(e);
    }

    @Override
    @Transactional
    public LedgerEntryResponse correct(UUID entryId) {
        LedgerEntry original = reversible(entryId);
        // Satis, planli alis (saticinin carisinden) ve taksit hareketleri plana bagli: kasa ekranindan terslenirse
        // plan ile defter ayrisir.
        boolean plannedPurchase = original.getType() == LedgerEntryType.VEHICLE_PURCHASE
                && original.getSourceAccount().getType() == AccountType.BALANCE;
        if (original.getType() == LedgerEntryType.VEHICLE_SALE || original.getType() == LedgerEntryType.CONSIGNMENT_SETTLEMENT
                || plannedPurchase
                || coOwnershipService.isLinkedEntry(original)
                || (original.getType() == LedgerEntryType.OPENING_BALANCE && planRepository.existsByOpeningEntryId(original.getId()))
                || original.getSourceAccount().getType() == AccountType.PORTFOLIO
                || original.getTargetAccount().getType() == AccountType.PORTFOLIO
                || installmentRepository.existsByPaidEntryId(original.getId())) {
            throw new LinkedEntryException();
        }
        LedgerEntry reversal = writeReversal(original);
        if (original.getType() == LedgerEntryType.VEHICLE_EXPENSE) afterExpense(reversal);
        return toResponse(reversal, false);
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
                ? newEntry(payer.getTenantId(), LedgerEntryType.COLLECTION, balance, money, amountCents, date)
                : newEntry(payer.getTenantId(), LedgerEntryType.PAYMENT, money, balance, amountCents, date);
        e.setVehicle(vehicle);
        e.setDescription(description);
        return entryRepository.save(e);
    }

    @Override
    @Transactional
    public LedgerEntry writeOpenItems(Contact contact, PlanDirection direction, long amountCents, LocalDate date) {
        Account opening = systemAccount(contact.getTenantId(), AccountType.OPENING);
        Account balance = balanceAccount(contact);
        LedgerEntry e = direction == PlanDirection.SALE
                ? newEntry(contact.getTenantId(), LedgerEntryType.OPENING_BALANCE, opening, balance, amountCents, date)
                : newEntry(contact.getTenantId(), LedgerEntryType.OPENING_BALANCE, balance, opening, amountCents, date);
        e.setDescription(direction == PlanDirection.SALE ? "Geçmiş alacak (senet / çek)" : "Geçmiş borç (senet / çek)");
        return entryRepository.save(e);
    }

    @Override
    @Transactional
    public Endorsement writeEndorsement(Vehicle fromVehicle, Contact from, Vehicle toVehicle, Contact to, long amountCents,
                                        LocalDate date, String inDescription, String outDescription) {
        UUID tenantId = from.getTenantId();
        Account portfolio = systemAccount(tenantId, AccountType.PORTFOLIO);
        LedgerEntry in = newEntry(tenantId, LedgerEntryType.COLLECTION, balanceAccount(from), portfolio, amountCents, date);
        in.setVehicle(fromVehicle);
        in.setDescription(inDescription);
        LedgerEntry out = newEntry(tenantId, LedgerEntryType.PAYMENT, portfolio, balanceAccount(to), amountCents, date);
        out.setVehicle(toVehicle);
        out.setDescription(outDescription);
        return new Endorsement(entryRepository.save(in), entryRepository.save(out));
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
        // Satir kilidi: ayni hareket iki istekte birden terslenmesin (ikincisi bekler, sonra "zaten duzeltildi" alir)
        LedgerEntry original = entryRepository.lockById(entryId).orElseThrow(RecordNotFoundException::new);
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

        // Araca bagli masraflar gittikleri hesaba gore: aracin maliyeti (VEHICLE), galerinin genel gideri (EXPENSE,
        // konsinye) ya da sahibine yansitilan (BALANCE, konsinye). Duzeltmeler ters yonde oldugu icin kendiliginden duser.
        long vehicleExpense = 0, absorbed = 0, charged = 0;
        for (LedgerEntry e : entries) {
            if (e.getType() != LedgerEntryType.VEHICLE_EXPENSE) continue;
            if (touches(e, AccountType.VEHICLE)) vehicleExpense += signed(e, AccountType.VEHICLE);
            else if (touches(e, AccountType.EXPENSE)) absorbed += signed(e, AccountType.EXPENSE);
            else if (touches(e, AccountType.BALANCE)) charged += signed(e, AccountType.BALANCE);
        }
        boolean purchaseInLedger = activePurchase(vehicle.getId()) != null;
        boolean consignment = vehicle.getOwnership() == Ownership.CONSIGNMENT;
        boolean sold = vehicle.getStatus() != VehicleStatus.IN_STOCK && vehicle.getSalePriceCents() != null;
        Long sale = vehicle.getSalePriceCents();

        // Alis her zaman aracin gercek alis bedelinden: alis deftere yazilmamis olsa da maliyet dogru kalir.
        long purchase = consignment || vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents();
        long expense = consignment ? absorbed : vehicleExpense;
        long total = purchase + expense;
        Long commission = consignment ? ConsignmentMath.commission(vehicle, sale) : null;
        Long profit = !sold ? null : consignment ? (commission == null ? 0 : commission) - expense : sale - total;
        Long payout = consignment && sold && commission != null ? sale - commission : null;

        // Ortak arac: sahipler, katkilari, satildiysa kar paylari (dukkan payi dusulmus) ve alacaklari
        List<ShareResponse> shares = coOwnershipService.owners(vehicle);
        return new VehicleCost(vehicle.getId(), vehicle.getOwnership(), purchase, purchaseInLedger, expense, total, sale, profit,
                commission, payout, consignment ? charged : 0, shares, toResponse(entries));
    }

    private static boolean touches(LedgerEntry e, AccountType type) {
        return e.getTargetAccount().getType() == type || e.getSourceAccount().getType() == type;
    }

    /** Hesaba giren arti, cikan eksi. */
    private static long signed(LedgerEntry e, AccountType type) {
        return e.getTargetAccount().getType() == type ? e.getAmountCents() : -e.getAmountCents();
    }

    // ------------------------------------------------------------------ konsinye

    @Override
    @Transactional
    public void syncConsignmentSettlement(Vehicle vehicle) {
        List<LedgerEntry> active = activeVehicleEntries(vehicle.getId(), LedgerEntryType.CONSIGNMENT_SETTLEMENT);
        Long sale = vehicle.getSalePriceCents();
        boolean wanted = vehicle.getOwnership() == Ownership.CONSIGNMENT && vehicle.getStatus() != VehicleStatus.IN_STOCK
                && sale != null && vehicle.getConsignor() != null;
        long amount = 0;
        if (wanted) {
            Long commission = ConsignmentMath.commission(vehicle, sale);
            if (commission == null || commission > sale) throw new CommissionExceedsSaleException();
            amount = sale - commission;
        }
        wanted = wanted && amount > 0;   // komisyon satisin tamamiysa sahibine borc yok

        if (wanted && active.size() == 1) {
            LedgerEntry e = active.get(0);
            if (e.getAmountCents() == amount && e.getEntryDate().equals(vehicle.getSaleDate())
                    && vehicle.getConsignor().getId().equals(e.getSourceAccount().getContactId())) {
                return;   // degisiklik yok
            }
        }
        if (!wanted && active.isEmpty()) return;

        active.forEach(this::writeReversal);
        if (wanted) {
            LedgerEntry e = newEntry(vehicle.getTenantId(), LedgerEntryType.CONSIGNMENT_SETTLEMENT, balanceAccount(vehicle.getConsignor()),
                    systemAccount(vehicle.getTenantId(), AccountType.INCOME), amount, vehicle.getSaleDate());
            e.setVehicle(vehicle);
            e.setDescription("Konsinye satış: sahibine borç");
            entryRepository.save(e);
        }
    }

    @Override
    @Transactional
    public LedgerEntryResponse payContact(UUID contactId, ContactPaymentRequest r) {
        UUID tenantId = CurrentUser.tenantId();
        Contact contact = contactRepository.findById(contactId).orElseThrow(RecordNotFoundException::new);
        if (!contact.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        Account money = getMoneyAccount(r.accountId());
        LedgerEntry e = newEntry(tenantId, LedgerEntryType.PAYMENT, money, balanceAccount(contact), r.amountCents(), r.date());
        String description = blankToNull(r.description());
        e.setDescription(description == null ? "Ödeme" : description);
        return toResponse(entryRepository.save(e), false);
    }

    // ------------------------------------------------------------------ kasa islemleri

    @Override
    @Transactional
    public LedgerEntryResponse transfer(CapitalRequests.Transfer r) {
        Account from = getMoneyAccount(r.fromAccountId());
        Account to = getMoneyAccount(r.toAccountId());
        if (from.getId().equals(to.getId())) throw new SameAccountException();
        LedgerEntry e = newEntry(from.getTenantId(), LedgerEntryType.TRANSFER, from, to, r.amountCents(), r.date());
        String description = blankToNull(r.description());
        e.setDescription(description == null ? "Virman" : description);
        return toResponse(entryRepository.save(e), false);
    }

    @Override
    @Transactional
    public LedgerEntryResponse openingBalance(CapitalRequests.Opening r) {
        if ((r.accountId() == null) == (r.contactId() == null)) throw new InvalidAccountException();
        UUID tenantId = CurrentUser.tenantId();
        Account opening = systemAccount(tenantId, AccountType.OPENING);

        Account account;
        boolean intoAccount;   // true: OPENING -> hesap (hesabin bakiyesi artar)
        if (r.accountId() != null) {
            account = getMoneyAccount(r.accountId());
            intoAccount = true;
        } else {
            Contact contact = contactRepository.findById(r.contactId()).orElseThrow(RecordNotFoundException::new);
            if (!contact.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
            if (r.side() == null) throw new InvalidAccountException();
            account = balanceAccount(contact);
            intoAccount = r.side() == CapitalRequests.OpeningSide.RECEIVABLE;
        }
        // Gecmis senet / cek planlarinin acilis hareketleri sayilmaz (kisiye ayrica acilis bakiyesi girilebilir)
        if (activeEntries(entryRepository.byTypeTouching(LedgerEntryType.OPENING_BALANCE, account.getId())).stream()
                .anyMatch(e -> !planRepository.existsByOpeningEntryId(e.getId()))) {
            throw new OpeningBalanceExistsException();
        }
        LedgerEntry e = intoAccount
                ? newEntry(tenantId, LedgerEntryType.OPENING_BALANCE, opening, account, r.amountCents(), r.date())
                : newEntry(tenantId, LedgerEntryType.OPENING_BALANCE, account, opening, r.amountCents(), r.date());
        e.setDescription("Açılış bakiyesi");
        return toResponse(entryRepository.save(e), false);
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
        // Sadece pesin alis (kasa / bankadan); planli alis saticinin carisinden yazilir, hesap secimi yoktur.
        return activeVehiclePurchases(vehicleId).stream()
                .filter(e -> e.getSourceAccount().getType().isMoneyAccount())
                .map(e -> e.getSourceAccount().getId())
                .findFirst().orElse(null);
    }

    @Override
    @Transactional
    public LedgerEntry writePlannedPurchase(Vehicle vehicle, Contact payee, long amountCents, LocalDate date) {
        LedgerEntry e = newEntry(vehicle.getTenantId(), LedgerEntryType.VEHICLE_PURCHASE, balanceAccount(payee),
                vehicleAccount(vehicle), amountCents, date);
        e.setVehicle(vehicle);
        e.setDescription("Planlı alış");
        return entryRepository.save(e);
    }

    @Override
    @Transactional
    public void reverseVehiclePurchases(UUID vehicleId) {
        activeVehiclePurchases(vehicleId).forEach(this::writeReversal);
    }

    @Override
    @Transactional
    public void reverseVehicleEntries(UUID vehicleId) {
        activeEntries(entryRepository.findByVehicle_Id(vehicleId)).forEach(this::writeReversal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> activeVehiclePurchases(UUID vehicleId) {
        return activeVehicleEntries(vehicleId, LedgerEntryType.VEHICLE_PURCHASE);
    }

    /** Aracin bu turdeki gecerli hareketleri: duzeltme olmayan ve duzeltilmemis olanlar. */
    private List<LedgerEntry> activeVehicleEntries(UUID vehicleId, LedgerEntryType type) {
        return activeEntries(entryRepository.findByVehicle_IdAndType(vehicleId, type));
    }

    /** Gecerli hareketler: kendisi duzeltme olmayan ve duzeltilmemis olanlar. */
    private List<LedgerEntry> activeEntries(List<LedgerEntry> entries) {
        List<LedgerEntry> list = entries.stream()
                .filter(e -> e.getCorrectedEntryId() == null)
                .toList();
        if (list.isEmpty()) return List.of();
        Set<UUID> corrected = new HashSet<>(entryRepository.correctedIds(list.stream().map(LedgerEntry::getId).toList()));
        return list.stream().filter(e -> !corrected.contains(e.getId())).toList();
    }

    // ------------------------------------------------------------------ yardimcilar

    /** Duzeltilmemis ilk VEHICLE_PURCHASE hareketi (pesin alista tektir). */
    private LedgerEntry activePurchase(UUID vehicleId) {
        return activeVehiclePurchases(vehicleId).stream().findFirst().orElse(null);
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
            a.setName(switch (type) {
                case CASH -> "Kasa";
                case EXPENSE -> "Genel giderler";
                case OPENING -> "Açılış bakiyeleri";
                case PORTFOLIO -> "Çek / senet portföyü";
                default -> "Gelirler";
            });
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
        if (vehicle.getStatus() == VehicleStatus.REMOVED) throw new RecordNotFoundException();   // kaldirilan araca masraf yazilmaz
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

    /** Sermaye islemleri de (CapitalServiceImpl) ayni cevap bicimini kullanir. */
    static LedgerEntryResponse toResponse(LedgerEntry e, boolean corrected) {
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
