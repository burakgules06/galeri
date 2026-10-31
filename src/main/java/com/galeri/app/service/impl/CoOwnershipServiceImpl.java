package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CommissionExceedsSaleException;
import com.galeri.app.exception.runtime.InsufficientCapitalException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidOwnerReturnException;
import com.galeri.app.exception.runtime.InvalidSharesException;
import com.galeri.app.exception.runtime.OwnerReturnExistsException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PaymentPlan;
import com.galeri.app.model.entity.PaymentPlanVehicle;
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
import com.galeri.app.model.response.VehicleRef;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.PaymentPlanRepository;
import com.galeri.app.repository.PaymentPlanVehicleRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.repository.VehicleShareRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CoOwnerSettlementPolicy;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.util.CoOwnershipMath;
import com.galeri.app.util.ConsignmentMath;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoOwnershipServiceImpl implements CoOwnershipService {

    private final VehicleShareRepository shareRepository;
    private final VehicleRepository vehicleRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final PaymentPlanRepository planRepository;
    private final PaymentPlanVehicleRepository basketRepository;
    private final InstallmentRepository installmentRepository;
    private final PanelUserRepository panelUserRepository;
    private final CoOwnerSettlementPolicy policy;
    private final Clock clock;

    /** Aracin alis bedelinden bir odeme: pesin alis hareketi ya da odenmis alis taksitinin hareketi. */
    record Payment(UUID entryId, LocalDate date, OffsetDateTime createdAt, long amountCents, Account moneyAccount) {}

    /**
     * Ortagin bu aractaki durumu: odenmis katkisi; satildiysa kar payi, alacagi (tahsilattan alacagi toplam) ve su ana kadar
     * tahsil edilip ona dagitilan.
     */
    record Settlement(VehicleShare owner, long contributionCents, Long profitCents, Long receivableCents, Long collectedCents) {}

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
        List<PanelUser> users = new ArrayList<>();
        for (ShareRequest r : wanted) {
            if (r.userId() == null) throw new InvalidSharesException();
            PanelUser u = getUser(r.userId(), vehicle.getTenantId());
            if (!u.isCapitalHolder()) throw new InvalidSharesException();   // arac sahibi dukkan sahibi ya da sermayedar
            users.add(u);
        }
        List<Boolean> pools = users.stream().map(PanelUser::isOwner).toList();
        if (vehicle.getOwnership() == Ownership.CO_OWNED) validate(wanted, vehicle.getActualPurchaseCents(), pools);
        List<VehicleShare> existing = shareRepository.findByVehicleId(vehicle.getId());
        if (same(existing, wanted, pools)) return;

        shareRepository.deleteAll(existing);
        shareRepository.flush();   // (vehicle_id, user_id) tekilligi: silmeler eklemelerden once
        for (int k = 0; k < wanted.size(); k++) {
            ShareRequest r = wanted.get(k);
            PanelUser user = users.get(k);
            VehicleShare s = new VehicleShare();
            s.setTenantId(vehicle.getTenantId());
            s.setVehicleId(vehicle.getId());
            if (!user.isOwner()) capitalAccount(user);   // sermaye hesabi yoksa acilir (sermayesi 0 ile)
            s.setUser(user);
            s.setShareBp(r.shareBp());
            s.setProfitShareBp(r.profitShareOrDefault());
            s.setContributionSource(user.isOwner() ? ContributionSource.CAPITAL : r.sourceOrDefault());
            s.setPocketCents(s.getContributionSource() == ContributionSource.HYBRID ? r.pocketCents() : null);
            shareRepository.save(s);
        }
        shareRepository.flush();
    }

    /**
     * En az bir sermayedar, her kullanici bir kez; sahiplik paylari ve kar payi oranlari ayri ayri toplam 10000.
     * pools: satir dukkan sahibinin mi (katkisi her zaman kasadan, sermaye kaynagi yok). Hibritte cepten tutar dolu ve katkiyi asmaz.
     */
    static void validate(List<ShareRequest> owners, Long actualPurchaseCents, List<Boolean> pools) {
        if (owners == null || owners.isEmpty()) throw new InvalidSharesException();
        List<UUID> users = owners.stream().map(ShareRequest::userId).toList();
        if (users.contains(null) || new HashSet<>(users).size() != users.size() || !pools.contains(false)) throw new InvalidSharesException();
        if (owners.stream().mapToLong(ShareRequest::shareBp).sum() != CoOwnershipMath.FULL_BP
                || owners.stream().mapToLong(ShareRequest::profitShareOrDefault).sum() != CoOwnershipMath.FULL_BP) {
            throw new InvalidSharesException();
        }
        int poolIndex = indexOfPool(pools);
        List<Long> contributions = CoOwnershipMath.split(actualPurchaseCents == null ? 0 : actualPurchaseCents,
                owners.stream().map(ShareRequest::shareBp).toList(), poolIndex);
        for (int k = 0; k < owners.size(); k++) {
            ShareRequest o = owners.get(k);
            if (pools.get(k) && o.sourceOrDefault() != ContributionSource.CAPITAL) throw new InvalidSharesException();
            if (o.sourceOrDefault() == ContributionSource.HYBRID
                    && (o.pocketCents() == null || o.pocketCents() <= 0 || o.pocketCents() > contributions.get(k))) {
                throw new InvalidSharesException();
            }
        }
    }

    private static boolean same(List<VehicleShare> existing, List<ShareRequest> wanted, List<Boolean> pools) {
        if (existing.size() != wanted.size()) return false;
        Set<List<Object>> a = new HashSet<>(), b = new HashSet<>();
        for (VehicleShare s : existing) {
            a.add(List.of(String.valueOf(s.getUser().getId()), s.getShareBp(), s.getProfitShareBp(),
                    s.getContributionSource(), String.valueOf(s.getPocketCents())));
        }
        for (int k = 0; k < wanted.size(); k++) {
            ShareRequest r = wanted.get(k);
            ContributionSource source = pools.get(k) ? ContributionSource.CAPITAL : r.sourceOrDefault();
            b.add(List.of(String.valueOf(r.userId()), r.shareBp(), r.profitShareOrDefault(), source,
                    String.valueOf(source == ContributionSource.HYBRID ? r.pocketCents() : null)));
        }
        return a.equals(b);
    }

    // ------------------------------------------------------------------ katki

    @Override
    @Transactional
    public void syncContributions(Vehicle vehicle) {
        if (vehicle == null) return;   // aracsiz plan (gecmis senet / cek)
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
                        wanted.add(new Wanted(capitalAccount(o.getUser()), p.moneyAccount(), pocket, p.date(), "Ortak katkısı (cepten)"));
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
                // Ciroyla (portfoyden) odenen taksitte ortagin cepten katkisi kasaya girer
                Account money = e.getSourceAccount().getType().isMoneyAccount() ? e.getSourceAccount() : cashAccount(vehicle.getTenantId());
                payments.add(new Payment(e.getId(), e.getEntryDate(), e.getCreatedAt(), e.getAmountCents(), money));
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

    /** Sahip basina odenmis katki (userId -> kurus; dukkan sahibi (havuz) null anahtarinda). */
    private Map<UUID, Long> contributions(Vehicle vehicle, List<VehicleShare> owners, LocalDate upTo) {
        Map<UUID, Long> result = new HashMap<>();
        for (Payment p : contributingPayments(vehicle)) {
            if (upTo != null && p.date().isAfter(upTo)) continue;
            List<Long> parts = parts(p.amountCents(), owners);
            for (int k = 0; k < owners.size(); k++) result.merge(holderKey(owners.get(k)), parts.get(k), Long::sum);
        }
        return result;
    }

    private static UUID holderKey(VehicleShare owner) {
        return owner.isPool() ? null : owner.getUser().getId();
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
            Account account = capitalAccount(o.getUser());
            long cash = capital.getOrDefault(account.getId(), 0L) - bound.getOrDefault(o.getUser().getId(), 0L);
            if (cash < 0) throw new InsufficientCapitalException(o.getUser().getFullName());
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
            resyncProfitShares(vehicle);   // konsinye: tahsil edilen kisim sahibinin sermayesine
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

    /**
     * Ortaga nakit donus: kasa / banka -> CAPITAL(ortak), satis gununde; en fazla satis aninda ona dagitilan tahsilat payi
     * kadar (pesin kisim). Kalan tahsil edildikce sermayesine gecer.
     */
    private void writeReturns(Vehicle vehicle, List<OwnerReturnRequest> returns) {
        Map<UUID, Settlement> byUser = new HashMap<>();
        settlements(vehicle).stream().filter(s -> !s.owner().isPool()).forEach(s -> byUser.put(s.owner().getUser().getId(), s));
        Set<UUID> seen = new HashSet<>();
        for (OwnerReturnRequest r : returns) {
            Settlement s = byUser.get(r.userId());
            if (s == null || !seen.add(r.userId()) || r.accountId() == null) throw new InvalidOwnerReturnException();
            long available = s.collectedCents() == null ? 0 : s.collectedCents();
            long amount = r.mode() == OwnerReturnMode.CASH ? available : r.amountCents() == null ? 0 : r.amountCents();
            if (amount <= 0 || amount > available) throw new InvalidOwnerReturnException();
            LedgerEntry e = newEntry(vehicle.getTenantId(), LedgerEntryType.CAPITAL, moneyAccount(r.accountId(), vehicle.getTenantId()),
                    capitalAccount(s.owner().getUser()), amount, vehicle.getSaleDate());
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
        apply(vehicle, LedgerEntryType.CONSIGNMENT_SETTLEMENT,
                active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.CONSIGNMENT_SETTLEMENT)), List.of());
    }

    private List<LedgerEntry> activeReturns(Vehicle vehicle) {
        return active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.CAPITAL)).stream()
                .filter(e -> e.getTargetAccount().getType() == AccountType.CAPITAL).toList();
    }

    /**
     * Satilmis aracin tahsilat dagitimini defterle esitler. Tahsilat geldikce dagitilir (pesin satista hemen):
     * <ul>
     *   <li>Ortakli arac: satis gununde ortagin katkisi satisa gecer (INCOME -> CAPITAL); her tahsilat kar payi oranlariyla
     *       bolunur, ortagin parcasi sermayesine yazilir (CAPITAL -> INCOME). Ortagin masraf payi (sahiplik oraniyla) ve
     *       dukkan payi ilk tahsilatlardan duser. Dukkan sahibinin parcasi kasada kalir (havuz).</li>
     *   <li>Konsinye: sahibine borc yazilmaz; her tahsilatin (satis - komisyon) / satis kadari sahibinin sermayesine,
     *       kalan (komisyon) galerinin karidir.</li>
     * </ul>
     * Tahsil edilmemis kisim sahibinin alacagidir (pendingSaleShares); karsiliksiz cikan evrakta payi oraninda ondan duser.
     */
    @Override
    @Transactional
    public void resyncProfitShares(Vehicle vehicle) {
        entryRepository.flush();
        installmentRepository.flush();
        boolean sold = vehicle.getStatus() != VehicleStatus.IN_STOCK && vehicle.getSalePriceCents() != null;
        List<Wanted> coOwned = new ArrayList<>();
        List<Wanted> consignment = new ArrayList<>();
        if (sold && vehicle.getOwnership() == Ownership.CO_OWNED && policy.timing() == CoOwnerSettlementPolicy.Timing.AT_SALE) {
            coOwned = distribution(vehicle, true).wanted();
        }
        if (sold && vehicle.getOwnership() == Ownership.CONSIGNMENT && vehicle.getConsignor() != null) {
            consignment = consignmentDistribution(vehicle, true).wanted();
        }
        apply(vehicle, LedgerEntryType.VEHICLE_PROFIT_SHARE,
                active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_PROFIT_SHARE)), coOwned);
        apply(vehicle, LedgerEntryType.CONSIGNMENT_SETTLEMENT,
                active(entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.CONSIGNMENT_SETTLEMENT)), consignment);
    }

    /** Aracin satis tahsilati: plan taksitlerinden odenenler, tarih sirasiyla. Toplu satista aracin bedeli oraninda. */
    record Collection(LocalDate date, long amountCents, UUID entryId) {}

    private List<Collection> collections(Vehicle vehicle) {
        UUID planId;
        long price = 0, total = 0;
        boolean basket = false;
        Optional<PaymentPlan> own = planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.SALE);
        if (own.isPresent()) {
            planId = own.get().getId();
        } else {
            Optional<PaymentPlanVehicle> link = basketRepository.findByVehicleId(vehicle.getId());
            if (link.isEmpty()) return List.of();
            planId = link.get().getPlanId();
            price = link.get().getSalePriceCents();
            total = planRepository.findById(planId).map(PaymentPlan::getTotalCents).orElse(0L);
            basket = true;
        }
        record Paid(LocalDate date, OffsetDateTime createdAt, UUID id, long amount) {}
        List<Installment> paid = installmentRepository.findByPlanIds(List.of(planId)).stream().filter(Installment::isPaid).toList();
        Map<UUID, LedgerEntry> entries = new HashMap<>();
        entryRepository.findAllById(paid.stream().map(Installment::getPaidEntryId).toList()).forEach(e -> entries.put(e.getId(), e));
        List<Paid> list = new ArrayList<>();
        for (Installment i : paid) {
            LedgerEntry e = entries.get(i.getPaidEntryId());
            if (e == null) continue;
            list.add(new Paid(e.getEntryDate(), e.getCreatedAt(), e.getId(), i.getAmountCents()));
        }
        list.sort(Comparator.comparing(Paid::date)
                .thenComparing(Paid::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Paid::id));
        List<Collection> result = new ArrayList<>();
        long cum = 0, prev = 0;
        for (Paid p : list) {
            cum += p.amount();
            long mine = basket ? (total == 0 ? 0 : Math.floorDiv(cum * price, total)) : cum;
            if (mine - prev != 0) result.add(new Collection(p.date(), mine - prev, p.id()));
            prev = mine;
        }
        return result;
    }

    /** Ortakli aracin dagitimi: sahip basina durum ve defterde istenen hareketler. */
    record Distribution(List<Settlement> settlements, List<Wanted> wanted, Map<UUID, Map<UUID, Long>> byEntry) {}

    private Distribution distribution(Vehicle vehicle, boolean entries) {
        List<VehicleShare> owners = owners(vehicle.getId(), vehicle.getOwnership());
        if (owners.isEmpty()) return new Distribution(List.of(), List.of(), Map.of());
        Map<UUID, Long> contributions = contributions(vehicle, owners, null);
        boolean sold = vehicle.getStatus() != VehicleStatus.IN_STOCK && vehicle.getSalePriceCents() != null;
        if (!sold) {
            List<Settlement> list = owners.stream()
                    .map(o -> new Settlement(o, contributions.getOrDefault(holderKey(o), 0L), null, null, null)).toList();
            return new Distribution(list, List.of(), Map.of());
        }
        long sale = vehicle.getSalePriceCents();
        int pool = indexOfPool(owners.stream().map(VehicleShare::isPool).toList());
        List<Integer> profitBps = owners.stream().map(VehicleShare::getProfitShareBp).toList();
        List<Long> gross = CoOwnershipMath.split(sale, profitBps, pool);

        // Ortagin dusulecek payi (sahiplik oraniyla): masraf payi + satista henuz odenmemis alis borcundan payi (kalan senetleri
        // havuz satis parasindan oder); kar varsa dukkan payi (ortaklarin karindan, kar payi oranlariyla).
        long expenses = vehicleExpenses(vehicle.getId());
        long purchase = vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents();
        List<Long> purchaseShares = CoOwnershipMath.split(purchase, owners.stream().map(VehicleShare::getShareBp).toList(), pool);
        long[] deduct = new long[owners.size()];
        List<Integer> partners = new ArrayList<>();
        long partnersProfit = 0;
        for (int k = 0; k < owners.size(); k++) {
            if (owners.get(k).isPool()) continue;
            partners.add(k);
            long contribution = contributions.getOrDefault(holderKey(owners.get(k)), 0L);
            deduct[k] = Math.floorDiv(expenses * owners.get(k).getShareBp(), CoOwnershipMath.FULL_BP)
                    + Math.max(0, purchaseShares.get(k) - contribution);
            partnersProfit += gross.get(k) - deduct[k] - contribution;
        }
        long cut = CoOwnershipMath.shopCut(partnersProfit, vehicle.getShopCutType(), vehicle.getShopCutRateBp(), vehicle.getShopCutCents());
        if (cut > 0) {
            List<Long> cuts = MoneyMath.splitByWeights(cut, partners.stream().map(k -> (long) owners.get(k).getProfitShareBp()).toList());
            for (int j = 0; j < partners.size(); j++) deduct[partners.get(j)] += cuts.get(j);
        }

        Account income = entries ? systemAccount(vehicle.getTenantId(), AccountType.INCOME) : null;
        List<Wanted> wanted = new ArrayList<>();
        for (int k : entries ? partners : List.<Integer>of()) {
            long contribution = contributions.getOrDefault(holderKey(owners.get(k)), 0L);
            if (contribution > 0) {
                wanted.add(new Wanted(income, capitalAccount(owners.get(k).getUser()), contribution, vehicle.getSaleDate(),
                        "Ortak katkısı satışa geçti"));
            }
        }
        long[] given = new long[owners.size()];
        long cum = 0;
        Map<UUID, Map<UUID, Long>> byEntry = new HashMap<>();
        for (Collection c : collections(vehicle)) {
            cum += c.amountCents();
            List<Long> parts = CoOwnershipMath.split(cum, profitBps, pool);
            for (int k : partners) {
                long now = Math.max(0, parts.get(k) - deduct[k]);
                long delta = now - given[k];
                given[k] = now;
                if (delta != 0) byEntry.computeIfAbsent(c.entryId(), x -> new HashMap<>()).merge(owners.get(k).getUser().getId(), delta, Long::sum);
                if (!entries) continue;
                Account capital = capitalAccount(owners.get(k).getUser());
                if (delta > 0) wanted.add(new Wanted(capital, income, delta, c.date(), "Ortak tahsilat payı"));
                else if (delta < 0) wanted.add(new Wanted(income, capital, -delta, c.date(), "Ortak tahsilat payı (düzeltme)"));
            }
        }

        List<Settlement> list = new ArrayList<>();
        long partnersProfitAfter = 0;
        for (int k : partners) {
            partnersProfitAfter += Math.max(0, gross.get(k) - deduct[k]) - contributions.getOrDefault(holderKey(owners.get(k)), 0L);
        }
        for (int k = 0; k < owners.size(); k++) {
            VehicleShare o = owners.get(k);
            long contribution = contributions.getOrDefault(holderKey(o), 0L);
            if (o.isPool()) {
                // Dukkan sahibi havuzun kalani: arac kari - ortaklarin kari; alacagi = katkisi + kari (parasi zaten kasada)
                long profit = sale - purchase - expenses - partnersProfitAfter;
                list.add(new Settlement(o, contribution, profit, contribution + profit, null));
            } else {
                long entitled = Math.max(0, gross.get(k) - deduct[k]);
                list.add(new Settlement(o, contribution, entitled - contribution, entitled, given[k]));
            }
        }
        return new Distribution(list, wanted, byEntry);
    }

    /** Konsinye dagitimi: sahibin alacagi (satis - komisyon), tahsil edildikce sermayesine. */
    record ConsignmentDistribution(long entitledCents, long givenCents, List<Wanted> wanted, Map<UUID, Long> capitalByEntry) {}

    private ConsignmentDistribution consignmentDistribution(Vehicle vehicle, boolean entries) {
        long sale = vehicle.getSalePriceCents();
        Long commission = ConsignmentMath.commission(vehicle, sale);
        if (commission == null || commission > sale) throw new CommissionExceedsSaleException();
        long entitled = sale - commission;
        // Sahibine yansitilan masraflar (carisinde borc) once onun payindan duser; kalani sermayesine
        long charged = chargedToOwner(vehicle);
        Account income = entries ? systemAccount(vehicle.getTenantId(), AccountType.INCOME) : null;
        Account capital = entries ? capitalAccount(vehicle.getConsignor()) : null;
        Account balance = entries && charged > 0 ? balanceAccount(vehicle.getConsignor()) : null;
        List<Wanted> wanted = new ArrayList<>();
        long cum = 0, given = 0, netted = 0;
        Map<UUID, Long> capitalByEntry = new HashMap<>();
        for (Collection c : collections(vehicle)) {
            cum += c.amountCents();
            long now = sale == 0 ? 0 : Math.min(entitled, Math.floorDiv(cum * entitled, sale));
            long nowNetted = Math.min(now, charged);
            long toBalance = nowNetted - netted, toCapital = (now - nowNetted) - (given - netted);
            given = now;
            netted = nowNetted;
            if (toCapital != 0) capitalByEntry.merge(c.entryId(), toCapital, Long::sum);
            if (!entries) continue;
            if (toBalance > 0) wanted.add(new Wanted(balance, income, toBalance, c.date(), "Konsinye: yansıtılan masraf payından düşüldü"));
            else if (toBalance < 0) wanted.add(new Wanted(income, balance, -toBalance, c.date(), "Konsinye: masraf mahsubu (düzeltme)"));
            if (toCapital > 0) wanted.add(new Wanted(capital, income, toCapital, c.date(), "Konsinye tahsilat payı"));
            else if (toCapital < 0) wanted.add(new Wanted(income, capital, -toCapital, c.date(), "Konsinye tahsilat payı (düzeltme)"));
        }
        return new ConsignmentDistribution(entitled, given, wanted, capitalByEntry);
    }

    /** Konsinye araçta sahibine yansitilan masraflar (aracin masraflarindan sahibinin carisine giden, duzeltmeler duser). */
    private long chargedToOwner(Vehicle vehicle) {
        long total = 0;
        for (LedgerEntry e : entryRepository.findByVehicle_IdAndType(vehicle.getId(), LedgerEntryType.VEHICLE_EXPENSE)) {
            if (e.getTargetAccount().getType() == AccountType.BALANCE) total += e.getAmountCents();
            else if (e.getSourceAccount().getType() == AccountType.BALANCE) total -= e.getAmountCents();
        }
        return Math.max(0, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingShare> pendingSaleShares(UUID tenantId) {
        List<PendingShare> result = new ArrayList<>();
        for (Vehicle v : vehicleRepository.findByTenantIdAndOwnership(tenantId, Ownership.CO_OWNED)) {
            if (v.getStatus() == VehicleStatus.IN_STOCK || v.getSalePriceCents() == null) continue;
            for (Settlement s : distribution(v, false).settlements()) {
                if (s.owner().isPool() || s.receivableCents() == null) continue;
                long left = s.receivableCents() - s.collectedCents();
                if (left != 0) result.add(new PendingShare(s.owner().getUser().getId(), ref(v), left));
            }
        }
        for (Vehicle v : vehicleRepository.findByTenantIdAndOwnership(tenantId, Ownership.CONSIGNMENT)) {
            if (v.getStatus() == VehicleStatus.IN_STOCK || v.getSalePriceCents() == null || v.getConsignor() == null) continue;
            ConsignmentDistribution d = consignmentDistribution(v, false);
            long left = d.entitledCents() - d.givenCents();
            if (left != 0) result.add(new PendingShare(v.getConsignor().getId(), ref(v), left));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public long partnersProfit(Vehicle vehicle) {
        if (vehicle.getOwnership() != Ownership.CO_OWNED) return 0;
        return settlements(vehicle).stream().filter(s -> !s.owner().isPool() && s.profitCents() != null)
                .mapToLong(Settlement::profitCents).sum();
    }

    @Override
    @Transactional(readOnly = true)
    public ConsignmentShare consignmentShare(Vehicle vehicle) {
        if (vehicle.getOwnership() != Ownership.CONSIGNMENT || vehicle.getConsignor() == null
                || vehicle.getStatus() == VehicleStatus.IN_STOCK || vehicle.getSalePriceCents() == null) return null;
        ConsignmentDistribution d = consignmentDistribution(vehicle, false);
        return new ConsignmentShare(d.entitledCents(), d.givenCents());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<UUID, Long>> moneyEntryShares(UUID tenantId) {
        Map<UUID, Map<UUID, Long>> result = new HashMap<>();
        for (Vehicle v : vehicleRepository.findByTenantIdAndOwnership(tenantId, Ownership.CO_OWNED)) {
            List<VehicleShare> owners = owners(v.getId(), v.getOwnership());
            if (owners.isEmpty()) continue;
            // Alis odemesi: ortagin katkisi o odemenin hesabindan cikti
            for (Payment p : contributingPayments(v)) {
                List<Long> parts = parts(p.amountCents(), owners);
                for (int k = 0; k < owners.size(); k++) {
                    if (owners.get(k).isPool() || parts.get(k) == 0) continue;
                    result.computeIfAbsent(p.entryId(), x -> new HashMap<>()).merge(owners.get(k).getUser().getId(), -parts.get(k), Long::sum);
                }
            }
            // Satis tahsilati: ortagin payi tahsilatin girdigi hesaba
            if (v.getStatus() != VehicleStatus.IN_STOCK && v.getSalePriceCents() != null) {
                distribution(v, false).byEntry().forEach((entry, users) ->
                        users.forEach((user, cents) -> result.computeIfAbsent(entry, x -> new HashMap<>()).merge(user, cents, Long::sum)));
            }
        }
        for (Vehicle v : vehicleRepository.findByTenantIdAndOwnership(tenantId, Ownership.CONSIGNMENT)) {
            if (v.getStatus() == VehicleStatus.IN_STOCK || v.getSalePriceCents() == null || v.getConsignor() == null) continue;
            UUID user = v.getConsignor().getId();
            consignmentDistribution(v, false).capitalByEntry().forEach((entry, cents) ->
                    result.computeIfAbsent(entry, x -> new HashMap<>()).merge(user, cents, Long::sum));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> vehicleUsers(UUID tenantId, java.util.Collection<UUID> vehicleIds) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        if (vehicleIds.isEmpty()) return result;
        UUID pool = poolOwnerId(tenantId);
        Map<UUID, List<UUID>> shares = new HashMap<>();
        for (VehicleShare s : shareRepository.findByVehicleIdIn(vehicleIds)) {
            shares.computeIfAbsent(s.getVehicleId(), k -> new ArrayList<>()).add(s.getUser().getId());
        }
        for (Vehicle v : vehicleRepository.findAllById(vehicleIds)) {
            if (!v.getTenantId().equals(tenantId)) continue;
            List<UUID> users = switch (v.getOwnership()) {
                case CO_OWNED -> shares.getOrDefault(v.getId(), List.of());
                case CONSIGNMENT -> v.getConsignor() == null ? List.of() : List.of(v.getConsignor().getId());
                default -> pool == null ? List.of() : List.of(pool);
            };
            result.put(v.getId(), users);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public UUID poolOwnerId(UUID tenantId) {
        return panelUserRepository.findPoolOwner(tenantId).map(PanelUser::getId).orElse(null);
    }

    private static VehicleRef ref(Vehicle v) {
        return new VehicleRef(v.getId(), v.getPlate(), v.getMake(), v.getModel());
    }

    /** Sahiplerin durumu (katki; satildiysa kar payi, alacak ve tahsil edilip dagitilan). */
    private List<Settlement> settlements(Vehicle vehicle) {
        return distribution(vehicle, false).settlements();
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
        return settlements(vehicle).stream().map(s -> {
            VehicleShare o = s.owner();
            return new ShareResponse(o.getUser().getId(), o.getUser().getFullName(), o.isPool(),
                    o.getShareBp(), o.getProfitShareBp(), o.getContributionSource(), o.getPocketCents(),
                    s.contributionCents(), s.profitCents(), s.receivableCents(), s.collectedCents());
        }).toList();
    }

    @Override
    public boolean isLinkedEntry(LedgerEntry e) {
        return e.getType() == LedgerEntryType.VEHICLE_PROFIT_SHARE || e.getType() == LedgerEntryType.CONSIGNMENT_SETTLEMENT
                || (e.getType() == LedgerEntryType.CAPITAL && e.getVehicle() != null && e.getSourceAccount().getType() == AccountType.CAPITAL);
    }

    // ------------------------------------------------------------------ yardimcilar

    private List<VehicleShare> owners(UUID vehicleId, Ownership ownership) {
        return ownership == Ownership.CO_OWNED && vehicleId != null ? shareRepository.findByVehicleIdWithUser(vehicleId) : List.of();
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

    /** Kullanicinin sermaye hesabi; yoksa acilir. */
    private Account capitalAccount(PanelUser user) {
        return userAccount(user, AccountType.CAPITAL);
    }

    private Account balanceAccount(PanelUser user) {
        return userAccount(user, AccountType.BALANCE);
    }

    private Account userAccount(PanelUser user, AccountType type) {
        UUID tenantId = user.getTenant().getId();
        return accountRepository.findFirstByTenantIdAndTypeAndUserId(tenantId, type, user.getId())
                .orElseGet(() -> {
                    Account a = new Account();
                    a.setTenantId(tenantId);
                    a.setType(type);
                    a.setUserId(user.getId());
                    a.setName(user.getFullName());
                    return accountRepository.save(a);
                });
    }

    private Account cashAccount(UUID tenantId) {
        return accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.CASH).orElseGet(() -> {
            Account a = new Account();
            a.setTenantId(tenantId);
            a.setType(AccountType.CASH);
            a.setName("Kasa");
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

    private PanelUser getUser(UUID id, UUID tenantId) {
        PanelUser u = panelUserRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (u.getTenant() == null || !u.getTenant().getId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        return u;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(VehicleServiceImpl.DEALER_ZONE));
    }
}
