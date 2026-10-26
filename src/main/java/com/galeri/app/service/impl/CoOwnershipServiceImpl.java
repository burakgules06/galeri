package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InsufficientCapitalException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidOwnerReturnException;
import com.galeri.app.exception.runtime.InvalidSharesException;
import com.galeri.app.exception.runtime.OwnerReturnExistsException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.entity.VehicleShare;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContributionSource;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.OwnerReturnMode;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.OwnerReturnRequest;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.response.ShareResponse;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.PaymentPlanRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.repository.VehicleShareRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CoOwnerSettlementPolicy;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.util.CoOwnershipMath;
import com.galeri.app.util.MoneyMath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoOwnershipServiceImpl implements CoOwnershipService {

    static final String POOL_NAME = "Havuz";

    private final VehicleShareRepository shareRepository;
    private final VehicleRepository vehicleRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final PaymentPlanRepository planRepository;
    private final InstallmentRepository installmentRepository;
    private final PanelUserRepository panelUserRepository;
    private final CoOwnerSettlementPolicy policy;
    private final Clock clock;

    /** Aracin alis bedelinden bir odeme: pesin alis hareketi ya da odenmis alis taksitinin hareketi. */
    record Payment(UUID entryId, LocalDate date, OffsetDateTime createdAt, long amountCents, Account moneyAccount) {}

    /** Ortagin bu aractaki durumu: odenmis katkisi, cepten kismi; satildiysa kar payi (dukkan payi dusulmus). */
    record Settlement(VehicleShare owner, long contributionCents, Long profitCents) {
        Long receivableCents() {
            return profitCents == null ? null : contributionCents + profitCents;
        }
    }

    /** Defterde istenen hareket: kaynak, hedef, tutar, tarih (diff icin). */
    record Wanted(Account source, Account target, long amountCents, LocalDate date, String description) {
        boolean matches(LedgerEntry e) {
            return e.getSourceAccount().getId().equals(source.getId()) && e.getTargetAccount().getId().equals(target.getId())
                    && e.getAmountCents() == amountCents && e.getEntryDate().equals(date);
        }
    }

    // ------------------------------------------------------------------ sahipler

    @Override
    @Transactional
    public void syncOwners(Vehicle vehicle, List<ShareRequest> requests) {
        List<ShareRequest> wanted = vehicle.getOwnership() == Ownership.CO_OWNED ? requests : List.of();
        if (vehicle.getOwnership() == Ownership.CO_OWNED) validate(wanted, vehicle.getActualPurchaseCents());
        List<VehicleShare> existing = shareRepository.findByVehicleId(vehicle.getId());
        if (same(existing, wanted)) return;

        shareRepository.deleteAll(existing);
        shareRepository.flush();   // (vehicle_id, contact_id) ve Havuz tekilligi: silmeler eklemelerden once
        for (ShareRequest r : wanted) {
            VehicleShare s = new VehicleShare();
            s.setTenantId(vehicle.getTenantId());
            s.setVehicleId(vehicle.getId());
            if (r.contactId() != null) {
                Contact contact = getContact(r.contactId(), vehicle.getTenantId());
                capitalAccount(contact);   // ortak olmayan kisi ortak olur (sermayesi 0 ile)
                s.setContact(contact);
            }
            s.setShareBp(r.shareBp());
            s.setProfitShareBp(r.profitShareOrDefault());
            s.setContributionSource(r.contactId() == null ? ContributionSource.CAPITAL : r.sourceOrDefault());
            s.setPocketCents(s.getContributionSource() == ContributionSource.HYBRID ? r.pocketCents() : null);
            shareRepository.save(s);
        }
        shareRepository.flush();
    }

    /**
     * En az bir ortak, Havuz en fazla bir, her kisi bir kez; sahiplik paylari ve kar payi oranlari ayri ayri toplam 10000.
     * Havuzun katkisi her zaman kasadan (sermaye kaynagi yok). Hibritte cepten tutar dolu ve katkiyi asmaz.
     */
    static void validate(List<ShareRequest> owners, Long actualPurchaseCents) {
        if (owners == null || owners.isEmpty()) throw new InvalidSharesException();
        long pools = owners.stream().filter(o -> o.contactId() == null).count();
        List<UUID> people = owners.stream().map(ShareRequest::contactId).filter(Objects::nonNull).toList();
        if (pools > 1 || people.isEmpty() || new HashSet<>(people).size() != people.size()) throw new InvalidSharesException();
        if (owners.stream().mapToLong(ShareRequest::shareBp).sum() != CoOwnershipMath.FULL_BP
                || owners.stream().mapToLong(ShareRequest::profitShareOrDefault).sum() != CoOwnershipMath.FULL_BP) {
            throw new InvalidSharesException();
        }
        int poolIndex = indexOfPool(owners.stream().map(o -> o.contactId() == null).toList());
        List<Long> contributions = CoOwnershipMath.split(actualPurchaseCents == null ? 0 : actualPurchaseCents,
                owners.stream().map(ShareRequest::shareBp).toList(), poolIndex);
        for (int k = 0; k < owners.size(); k++) {
            ShareRequest o = owners.get(k);
            if (o.contactId() == null && o.sourceOrDefault() != ContributionSource.CAPITAL) throw new InvalidSharesException();
            if (o.sourceOrDefault() == ContributionSource.HYBRID
                    && (o.pocketCents() == null || o.pocketCents() <= 0 || o.pocketCents() > contributions.get(k))) {
                throw new InvalidSharesException();
            }
        }
    }

    private static boolean same(List<VehicleShare> existing, List<ShareRequest> wanted) {
        if (existing.size() != wanted.size()) return false;
        Set<List<Object>> a = new HashSet<>(), b = new HashSet<>();
        for (VehicleShare s : existing) {
            a.add(List.of(String.valueOf(s.isPool() ? null : s.getContact().getId()), s.getShareBp(), s.getProfitShareBp(),
                    s.getContributionSource(), String.valueOf(s.getPocketCents())));
        }
        for (ShareRequest r : wanted) {
            ContributionSource source = r.contactId() == null ? ContributionSource.CAPITAL : r.sourceOrDefault();
            b.add(List.of(String.valueOf(r.contactId()), r.shareBp(), r.profitShareOrDefault(), source,
                    String.valueOf(source == ContributionSource.HYBRID ? r.pocketCents() : null)));
        }
        return a.equals(b);
    }

    // ------------------------------------------------------------------ katki

    @Override
    @Transactional
    public void syncContributions(Vehicle vehicle) {
        List<VehicleShare> owners = owners(vehicle.getId(), vehicle.getOwnership());
        List<Wanted> wanted = new ArrayList<>();
        if (!owners.isEmpty()) {
            // Cepten kisim: her odemede ortagin payi kadar; hibritte cepten tutar bitene kadar once cepten
            Map<UUID, Long> pocketLeft = new HashMap<>();
            owners.stream().filter(o -> o.getContributionSource() == ContributionSource.HYBRID)
                    .forEach(o -> pocketLeft.put(o.getId(), o.getPocketCents()));
            for (Payment p : contributingPayments(vehicle)) {
                List<Long> parts = parts(p.amountCents(), owners);
                for (int k = 0; k < owners.size(); k++) {
                    VehicleShare o = owners.get(k);
                    if (o.isPool()) continue;
                    long pocket = switch (o.getContributionSource()) {
                        case POCKET -> parts.get(k);
                        case HYBRID -> Math.min(parts.get(k), pocketLeft.get(o.getId()));
                        case CAPITAL -> 0;
                    };
                    if (o.getContributionSource() == ContributionSource.HYBRID) pocketLeft.merge(o.getId(), -pocket, Long::sum);
                    if (pocket > 0) {
                        wanted.add(new Wanted(capitalAccount(o.getContact()), p.moneyAccount(), pocket, p.date(), "Ortak katkısı (cepten)"));
                    }
                }
            }
        }
        // Cepten katki: CAPITAL(ortak) -> kasa / banka, arac bagli (ortaga nakit donus ters yondedir)
        List<LedgerEntry> existing = active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.CAPITAL)).stream()
                .filter(e -> e.getSourceAccount().getType() == AccountType.CAPITAL).toList();
        apply(vehicle, LedgerEntryType.CAPITAL, existing, wanted);

        if (!owners.isEmpty()) checkCashCapital(vehicle.getTenantId(), owners);
        if (vehicle.getStatus() != VehicleStatus.IN_STOCK) resyncProfitShares(vehicle);
    }

    /**
     * Katkiya sayilan odemeler: pesin alis (kasa / bankadan) ve odenmis alis taksitleri, tarih sirasiyla. Satilmis aracta
     * satis gununden sonraki odemeler sayilmaz: kalan senetleri satis parasindan havuz oder.
     */
    private List<Payment> contributingPayments(Vehicle vehicle) {
        List<Payment> payments = new ArrayList<>();
        for (LedgerEntry e : active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PURCHASE))) {
            if (e.getSourceAccount().getType().isMoneyAccount()) {
                payments.add(new Payment(e.getId(), e.getEntryDate(), e.getCreatedAt(), e.getAmountCents(), e.getSourceAccount()));
            }
        }
        planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.PURCHASE).ifPresent(plan -> {
            List<UUID> paid = installmentRepository.findByPlanIds(List.of(plan.getId())).stream()
                    .filter(Installment::isPaid).map(Installment::getPaidEntryId).toList();
            for (LedgerEntry e : entryRepository.findAllById(paid)) {
                payments.add(new Payment(e.getId(), e.getEntryDate(), e.getCreatedAt(), e.getAmountCents(), e.getSourceAccount()));
            }
        });
        LocalDate saleDate = vehicle.getStatus() == VehicleStatus.IN_STOCK ? null : vehicle.getSaleDate();
        return payments.stream()
                .filter(p -> saleDate == null || !p.date().isAfter(saleDate))
                .sorted(Comparator.comparing(Payment::date)
                        .thenComparing(Payment::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Payment::entryId))
                .toList();
    }

    /** Sahip basina odenmis katki (contactId -> kurus; Havuz null anahtarinda). */
    private Map<UUID, Long> contributions(Vehicle vehicle, List<VehicleShare> owners, LocalDate upTo) {
        Map<UUID, Long> result = new HashMap<>();
        for (Payment p : contributingPayments(vehicle)) {
            if (upTo != null && p.date().isAfter(upTo)) continue;
            List<Long> parts = parts(p.amountCents(), owners);
            for (int k = 0; k < owners.size(); k++) result.merge(contactId(owners.get(k)), parts.get(k), Long::sum);
        }
        return result;
    }

    private static UUID contactId(VehicleShare owner) {
        return owner.isPool() ? null : owner.getContact().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> boundCapital(UUID tenantId, LocalDate asOf) {
        Map<UUID, Long> bound = new HashMap<>();
        for (Vehicle v : vehicleRepository.findByTenantIdAndOwnership(tenantId, Ownership.CO_OWNED)) {
            if (v.getPurchaseDate().isAfter(asOf)) continue;
            boolean soldByThen = v.getStatus() != VehicleStatus.IN_STOCK && v.getSaleDate() != null && !v.getSaleDate().isAfter(asOf);
            if (soldByThen) continue;
            contributions(v, owners(v.getId(), v.getOwnership()), asOf).forEach((id, c) -> {
                if (id != null) bound.merge(id, c, Long::sum);   // Havuzun katkisi kimsenin sermayesine bagli degil
            });
        }
        return bound;
    }

    /** Ortaklarin nakitteki sermayesi (sermaye - araclarda bagli) eksiye dusmemeli. */
    private void checkCashCapital(UUID tenantId, List<VehicleShare> owners) {
        Map<UUID, Long> capital = CapitalServiceImpl.capitalByAccount(entryRepository.touchingType(tenantId, AccountType.CAPITAL), null);
        Map<UUID, Long> bound = boundCapital(tenantId, today());
        for (VehicleShare o : owners) {
            if (o.isPool()) continue;
            Account account = capitalAccount(o.getContact());
            long cash = capital.getOrDefault(account.getId(), 0L) - bound.getOrDefault(o.getContact().getId(), 0L);
            if (cash < 0) throw new InsufficientCapitalException(o.getContact().getName());
        }
    }

    // ------------------------------------------------------------------ satis

    @Override
    @Transactional
    public void onSale(Vehicle vehicle, List<OwnerReturnRequest> returns) {
        List<OwnerReturnRequest> requested = returns == null ? List.of()
                : returns.stream().filter(r -> r.mode() != OwnerReturnMode.KEEP).toList();
        if (vehicle.getOwnership() != Ownership.CO_OWNED) {
            if (!requested.isEmpty()) throw new InvalidOwnerReturnException();
            return;
        }
        switch (policy.timing()) {
            case AT_SALE -> {
                resyncProfitShares(vehicle);
                writeReturns(vehicle, requested);
            }
        }
        checkCashCapital(vehicle.getTenantId(), owners(vehicle.getId(), vehicle.getOwnership()));
    }

    /** Ortaga nakit donus: kasa / banka -> CAPITAL(ortak), satis gununde; en fazla alacagi (katki + kar payi) kadar. */
    private void writeReturns(Vehicle vehicle, List<OwnerReturnRequest> returns) {
        Map<UUID, Settlement> byContact = new HashMap<>();
        settlements(vehicle).stream().filter(s -> !s.owner().isPool()).forEach(s -> byContact.put(s.owner().getContact().getId(), s));
        Set<UUID> seen = new HashSet<>();
        for (OwnerReturnRequest r : returns) {
            Settlement s = byContact.get(r.contactId());
            if (s == null || !seen.add(r.contactId()) || r.accountId() == null) throw new InvalidOwnerReturnException();
            long receivable = s.receivableCents();
            long amount = r.mode() == OwnerReturnMode.CASH ? receivable : r.amountCents() == null ? 0 : r.amountCents();
            if (amount <= 0 || amount > receivable) throw new InvalidOwnerReturnException();
            LedgerEntry e = newEntry(vehicle.getTenantId(), LedgerEntryType.CAPITAL, moneyAccount(r.accountId(), vehicle.getTenantId()),
                    capitalAccount(s.owner().getContact()), amount, vehicle.getSaleDate());
            e.setVehicle(vehicle);
            e.setDescription("Ortağa dönüş");
            entryRepository.save(e);
        }
    }

    @Override
    @Transactional
    public void onUndoSale(Vehicle vehicle) {
        if (!activeReturns(vehicle).isEmpty()) throw new OwnerReturnExistsException();
        apply(vehicle, LedgerEntryType.VEHICLE_PROFIT_SHARE,
                active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PROFIT_SHARE)), List.of());
    }

    private List<LedgerEntry> activeReturns(Vehicle vehicle) {
        return active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.CAPITAL)).stream()
                .filter(e -> e.getTargetAccount().getType() == AccountType.CAPITAL).toList();
    }

    @Override
    @Transactional
    public void resyncProfitShares(Vehicle vehicle) {
        List<Wanted> wanted = new ArrayList<>();
        boolean settled = policy.timing() == CoOwnerSettlementPolicy.Timing.AT_SALE && vehicle.getOwnership() == Ownership.CO_OWNED
                && vehicle.getStatus() != VehicleStatus.IN_STOCK && vehicle.getSalePriceCents() != null;
        if (settled) {
            Account income = systemAccount(vehicle.getTenantId(), AccountType.INCOME);
            for (Settlement s : settlements(vehicle)) {
                if (s.owner().isPool() || s.profitCents() == null || s.profitCents() == 0) continue;
                Account capital = capitalAccount(s.owner().getContact());
                long share = s.profitCents();
                if (share > 0) {
                    wanted.add(new Wanted(capital, income, share, vehicle.getSaleDate(), "Ortak kâr payı"));
                    continue;
                }
                // Zarar once katkidan (sermayeden) duser; katkiyi asan kisim ortagin carisine: galeriye borclanir
                long loss = -share;
                long fromCapital = Math.min(loss, Math.max(0, s.contributionCents()));
                if (fromCapital > 0) wanted.add(new Wanted(income, capital, fromCapital, vehicle.getSaleDate(), "Ortak zarar payı"));
                if (loss > fromCapital) {
                    wanted.add(new Wanted(income, balanceAccount(s.owner().getContact()), loss - fromCapital, vehicle.getSaleDate(),
                            "Ortak zarar payı (katkıyı aşan, cariye)"));
                }
            }
        }
        apply(vehicle, LedgerEntryType.VEHICLE_PROFIT_SHARE,
                active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PROFIT_SHARE)), wanted);
    }

    /**
     * Sahiplerin durumu. Kar = satis - (gercek alis + arac masraflari); kar payi orani ile bolunur (kurus farki Havuz'a).
     * Kar varsa dukkan payi ortaklarin payindan dusulur (havuza kalir), ortaklar arasinda kar payi oranlarina gore.
     */
    private List<Settlement> settlements(Vehicle vehicle) {
        List<VehicleShare> owners = owners(vehicle.getId(), vehicle.getOwnership());
        if (owners.isEmpty()) return List.of();
        Map<UUID, Long> contributions = contributions(vehicle, owners, null);
        List<Long> shares = null;
        boolean sold = vehicle.getStatus() != VehicleStatus.IN_STOCK && vehicle.getSalePriceCents() != null;
        if (sold) {
            long profit = vehicle.getSalePriceCents() - (vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents())
                    - vehicleExpenses(vehicle.getId());
            int poolIndex = indexOfPool(owners.stream().map(VehicleShare::isPool).toList());
            shares = new ArrayList<>(CoOwnershipMath.split(profit, owners.stream().map(VehicleShare::getProfitShareBp).toList(), poolIndex));
            List<Integer> partners = new ArrayList<>();
            long partnersProfit = 0;
            for (int k = 0; k < owners.size(); k++) {
                if (owners.get(k).isPool()) continue;
                partners.add(k);
                partnersProfit += shares.get(k);
            }
            long cut = CoOwnershipMath.shopCut(partnersProfit, vehicle.getShopCutType(), vehicle.getShopCutRateBp(), vehicle.getShopCutCents());
            if (cut > 0) {
                List<Long> cuts = MoneyMath.splitByWeights(cut, partners.stream().map(k -> (long) owners.get(k).getProfitShareBp()).toList());
                for (int j = 0; j < partners.size(); j++) {
                    int k = partners.get(j);
                    shares.set(k, shares.get(k) - cuts.get(j));
                }
                if (poolIndex >= 0) shares.set(poolIndex, shares.get(poolIndex) + cut);
            }
        }
        List<Settlement> result = new ArrayList<>();
        for (int k = 0; k < owners.size(); k++) {
            VehicleShare o = owners.get(k);
            long contribution = contributions.getOrDefault(contactId(o), 0L);
            result.add(new Settlement(o, contribution, shares == null ? null : shares.get(k)));
        }
        return result;
    }

    /** Aracin maliyetine giren masraflar (VEHICLE hesabina net giris; duzeltmeler kendiliginden duser). */
    private long vehicleExpenses(UUID vehicleId) {
        long total = 0;
        for (LedgerEntry e : entryRepository.findByVehicle_IdAndType(vehicleId, LedgerEntryType.VEHICLE_EXPENSE)) {
            if (e.getTargetAccount().getType() == AccountType.VEHICLE) total += e.getAmountCents();
            else if (e.getSourceAccount().getType() == AccountType.VEHICLE) total -= e.getAmountCents();
        }
        return total;
    }

    // ------------------------------------------------------------------ gosterim

    @Override
    @Transactional(readOnly = true)
    public List<ShareResponse> owners(Vehicle vehicle) {
        if (vehicle.getId() == null) return List.of();
        // Satilmis aracta kar payi defterden degil hesaptan: AT_SALE'de ikisi aynidir (resyncProfitShares)
        return settlements(vehicle).stream().map(s -> {
            VehicleShare o = s.owner();
            return new ShareResponse(o.isPool() ? null : o.getContact().getId(), o.isPool() ? POOL_NAME : o.getContact().getName(),
                    o.getShareBp(), o.getProfitShareBp(), o.getContributionSource(), o.getPocketCents(),
                    s.contributionCents(), s.profitCents(), s.receivableCents());
        }).toList();
    }

    @Override
    public boolean isLinkedEntry(LedgerEntry e) {
        return e.getType() == LedgerEntryType.VEHICLE_PROFIT_SHARE
                || (e.getType() == LedgerEntryType.CAPITAL && e.getVehicle() != null && e.getSourceAccount().getType() == AccountType.CAPITAL);
    }

    // ------------------------------------------------------------------ yardimcilar

    private List<VehicleShare> owners(UUID vehicleId, Ownership ownership) {
        return ownership == Ownership.CO_OWNED && vehicleId != null ? shareRepository.findByVehicleIdWithContact(vehicleId) : List.of();
    }

    private static List<Long> parts(long amount, List<VehicleShare> owners) {
        return CoOwnershipMath.split(amount, owners.stream().map(VehicleShare::getShareBp).toList(),
                indexOfPool(owners.stream().map(VehicleShare::isPool).toList()));
    }

    private static int indexOfPool(List<Boolean> isPool) {
        return isPool.indexOf(true);
    }

    /** Gecerli hareketleri istenenle esitler: fazlalar terslenir, eksikler yazilir (ayni olanlara dokunulmaz). */
    private void apply(Vehicle vehicle, LedgerEntryType type, List<LedgerEntry> existing, List<Wanted> wanted) {
        List<Wanted> missing = new ArrayList<>(wanted);
        for (LedgerEntry e : existing) {
            Wanted match = missing.stream().filter(w -> w.matches(e)).findFirst().orElse(null);
            if (match != null) missing.remove(match);
            else writeReversal(e);
        }
        for (Wanted w : missing) {
            LedgerEntry e = newEntry(vehicle.getTenantId(), type, w.source(), w.target(), w.amountCents(), w.date());
            e.setVehicle(vehicle);
            e.setDescription(w.description());
            entryRepository.save(e);
        }
    }

    /** Gecerli hareketler: kendisi duzeltme olmayan ve duzeltilmemis olanlar. */
    private List<LedgerEntry> active(List<LedgerEntry> entries) {
        List<LedgerEntry> list = entries.stream().filter(e -> e.getCorrectedEntryId() == null).toList();
        if (list.isEmpty()) return List.of();
        Set<UUID> corrected = new HashSet<>(entryRepository.correctedIds(list.stream().map(LedgerEntry::getId).toList()));
        return list.stream().filter(e -> !corrected.contains(e.getId())).toList();
    }

    private void writeReversal(LedgerEntry original) {
        LedgerEntry reversal = newEntry(original.getTenantId(), original.getType(), original.getTargetAccount(),
                original.getSourceAccount(), original.getAmountCents(), original.getEntryDate());
        reversal.setVehicle(original.getVehicle());
        reversal.setDescription("Düzeltme" + (original.getDescription() == null ? "" : ": " + original.getDescription()));
        reversal.setCorrectedEntryId(original.getId());
        entryRepository.save(reversal);
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

    /** Ortagin sermaye hesabi; yoksa acilir (kisi ortak olur). */
    private Account capitalAccount(Contact contact) {
        return contactAccount(contact, AccountType.CAPITAL);
    }

    private Account balanceAccount(Contact contact) {
        return contactAccount(contact, AccountType.BALANCE);
    }

    private Account contactAccount(Contact contact, AccountType type) {
        return accountRepository.findFirstByTenantIdAndTypeAndContactId(contact.getTenantId(), type, contact.getId())
                .orElseGet(() -> {
                    Account a = new Account();
                    a.setTenantId(contact.getTenantId());
                    a.setType(type);
                    a.setContactId(contact.getId());
                    a.setName(contact.getName());
                    return accountRepository.save(a);
                });
    }

    private Account systemAccount(UUID tenantId, AccountType type) {
        return accountRepository.findFirstByTenantIdAndType(tenantId, type).orElseGet(() -> {
            Account a = new Account();
            a.setTenantId(tenantId);
            a.setType(type);
            a.setName("Gelirler");
            return accountRepository.save(a);
        });
    }

    private Account moneyAccount(UUID id, UUID tenantId) {
        Account a = accountRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!a.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        if (!a.getType().isMoneyAccount()) throw new InvalidAccountException();
        return a;
    }

    private Contact getContact(UUID id, UUID tenantId) {
        Contact c = contactRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!c.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        return c;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(VehicleServiceImpl.DEALER_ZONE));
    }
}
