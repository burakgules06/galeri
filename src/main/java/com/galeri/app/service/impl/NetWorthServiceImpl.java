package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.enums.*;
import com.galeri.app.model.response.*;
import com.galeri.app.repository.*;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.*;
import com.galeri.app.util.CoOwnershipMath;
import com.galeri.app.util.MoneyMath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NetWorthServiceImpl implements NetWorthService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final VehicleRepository vehicles;
    private final InstallmentRepository installments;
    private final CapitalService capital;
    private final CoOwnershipService coOwnership;
    private final PanelUserRepository users;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NetWorthResponse summary() {
        UUID tenant = CurrentUser.tenantId();
        Map<UUID, Long> balances = new HashMap<>();
        entries.incomingTotals(tenant).forEach(r -> balances.merge((UUID) r[0], ((Number) r[1]).longValue(), Long::sum));
        entries.outgoingTotals(tenant).forEach(r -> balances.merge((UUID) r[0], -((Number) r[1]).longValue(), Long::sum));
        Map<AccountType, Long> totals = new EnumMap<>(AccountType.class);
        Map<UUID, Long> vehicleCosts = new HashMap<>();
        long receivable = 0, payable = 0;
        for (Account a : accounts.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenant, List.of(AccountType.values()))) {
            long b = balances.getOrDefault(a.getId(), 0L);
            totals.merge(a.getType(), b, Long::sum);
            if (a.getType() == AccountType.VEHICLE) vehicleCosts.put(a.getVehicleId(), b);
            if (a.getType() == AccountType.BALANCE) {
                receivable += Math.max(0, b);
                payable += Math.max(0, -b);
            }
        }
        Map<UUID, Long> debts = new HashMap<>();
        installments.findAllWithPlan(tenant).stream()
                .filter(i -> i.getPlan().getDirection() == PlanDirection.PURCHASE && i.getPaidEntryId() == null)
                .forEach(i -> debts.merge(i.getPlan().getVehicleId(), i.getAmountCents(), Long::sum));
        List<NetWorthResponse.StockVehicle> stock = new ArrayList<>();
        for (var v : vehicles.findByTenantIdAndStatus(tenant, VehicleStatus.IN_STOCK)) {
            if (v.getOwnership() == Ownership.CONSIGNMENT) continue;
            long cost = vehicleCosts.getOrDefault(v.getId(), 0L);
            long debt = debts.getOrDefault(v.getId(), 0L);
            List<ShareResponse> owners = coOwnership.owners(v);
            List<NetWorthResponse.Owner> shares = new ArrayList<>();
            if (owners.isEmpty()) {
                shares.add(new NetWorthResponse.Owner(null, "Dükkân", true, 10000, cost, debt, cost - debt, cost - debt));
            } else {
                int pool = -1;
                for (int k = owners.size() - 1; k >= 0; k--) if (owners.get(k).owner()) pool = k;
                List<Long> assets = CoOwnershipMath.split(cost, owners.stream().map(ShareResponse::shareBp).toList(), pool);
                List<Long> ownerDebts = CoOwnershipMath.split(debt, owners.stream().map(ShareResponse::shareBp).toList(), pool);
                for (int k = 0; k < owners.size(); k++) {
                    var o = owners.get(k);
                    long ownerDebt = ownerDebts.get(k);
                    shares.add(new NetWorthResponse.Owner(o.userId(), o.name(), o.owner(), o.shareBp(), assets.get(k), ownerDebt,
                            assets.get(k) - ownerDebt, o.contributionCents()));
                }
            }
            stock.add(new NetWorthResponse.StockVehicle(new VehicleRef(v.getId(), v.getPlate(), v.getMake(), v.getModel()), cost, debt, shares));
        }
        long stockTotal = stock.stream().mapToLong(NetWorthResponse.StockVehicle::costCents).sum();
        long cash = totals.getOrDefault(AccountType.CASH, 0L), bank = totals.getOrDefault(AccountType.BANK, 0L);
        long cap = -totals.getOrDefault(AccountType.CAPITAL, 0L), opening = -totals.getOrDefault(AccountType.OPENING, 0L);
        long soldCost = totals.getOrDefault(AccountType.VEHICLE, 0L) - stockTotal;
        long profit = -totals.getOrDefault(AccountType.INCOME, 0L) - totals.getOrDefault(AccountType.EXPENSE, 0L) - soldCost;
        // Satilmis ortakli / konsinye araclarda sahiplerin tahsil edilmemis alacagi gelir hesabinda bekler: sahiplerin
        // sermayesidir (kartlarinda alacak), dagitilmamis kar degil.
        long pendingShares = coOwnership.pendingSaleShares(tenant).stream().mapToLong(CoOwnershipService.PendingShare::cents).sum();
        cap += pendingShares;
        profit -= pendingShares;
        long total = cash + bank + stockTotal + receivable - payable;
        return new NetWorthResponse(cash, bank, stockTotal, receivable, payable, total, cap, opening, profit,
                total - cap - opening - profit, capital.partners(), stock);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CapitalResponses.Holder> holders() {
        List<CapitalResponses.Holder> all = allHolders();
        if (!CurrentUser.isInvestor()) return all;
        UUID me = CurrentUser.principal().userId();
        return all.stream().filter(h -> me.equals(h.userId())).toList();
    }

    private List<CapitalResponses.Holder> allHolders() {
        UUID tenant = CurrentUser.tenantId();
        NetWorthResponse w = summary();
        Optional<PanelUser> owner = users.findPoolOwner(tenant);
        UUID ownerId = owner.map(PanelUser::getId).orElse(null);
        long money = w.cashCents() + w.bankCents();

        // Sermayedara ait tahsil edilmemis evrak: onun alacagi (riski onda), dukkan sahibinin payinda sayilmaz
        Map<UUID, Long> pending = new java.util.HashMap<>();
        for (Object[] row : installments.pendingByHolder(tenant)) pending.put((UUID) row[0], ((Number) row[1]).longValue());
        // Satilmis ortakli / konsinye araclardan tahsil edilmemis payi: tahsil edildikce sermayesine gecer
        for (CoOwnershipService.PendingShare p : coOwnership.pendingSaleShares(tenant)) pending.merge(p.userId(), p.cents(), Long::sum);

        // Kullanicinin carisi (konsinye sahibi gibi): artisi bize borclu, eksisi biz ona borcluyuz
        Map<UUID, Long> userBalance = new java.util.HashMap<>();
        Map<UUID, Long> balances = new java.util.HashMap<>();
        for (Object[] r : entries.incomingTotals(tenant)) balances.merge((UUID) r[0], ((Number) r[1]).longValue(), Long::sum);
        for (Object[] r : entries.outgoingTotals(tenant)) balances.merge((UUID) r[0], -((Number) r[1]).longValue(), Long::sum);
        for (var a : accounts.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenant, List.of(AccountType.BALANCE))) {
            if (a.getUserId() != null) userBalance.merge(a.getUserId(), balances.getOrDefault(a.getId(), 0L), Long::sum);
        }

        List<CapitalResponses.Holder> raw = new ArrayList<>();
        long investorsCapital = 0, investorsCash = 0;
        long investorsReceivable = 0;
        for (CapitalResponses.Partner p : w.partners()) {
            if (p.owner()) continue;   // dukkan sahipleri havuzun kalani
            List<CapitalResponses.HolderVehicle> list = vehiclesOf(w, o -> p.userId().equals(o.userId()));
            CapitalBase base = users.findById(p.userId()).map(PanelUser::getCapitalBase).orElse(CapitalBase.ALL);
            long receivable = pending.getOrDefault(p.userId(), 0L);
            raw.add(new CapitalResponses.Holder(p.userId(), p.name(), false, base, p.capitalCents() + receivable, p.cashCents(),
                    p.boundCents(), receivable, 0, list, userBalance.getOrDefault(p.userId(), 0L)));
            investorsCapital += p.capitalCents() + receivable;
            investorsCash += p.cashCents();
            investorsReceivable += receivable;
        }
        // Dukkan sahibi havuzun kalani: ortaklarin odenmis katkisi dusulunce kalan stok araci degeri (masraflar dahil) onundur.
        List<CapitalResponses.HolderVehicle> ownerVehicles = ownerVehicles(w, ownerId);
        long ownerCapital = w.totalCents() - investorsCapital;
        long ownerCash = money - investorsCash;
        long ownerInVehicles = ownerVehicles.stream().mapToLong(CapitalResponses.HolderVehicle::cents).sum();
        long stockDebt = w.vehicles().stream().mapToLong(NetWorthResponse.StockVehicle::debtCents).sum();
        // Arac alis borcu arac net degerinden dusuldu; other'da yalnizca cari alacak/borc ve dukkan evraki kalir.
        long ownerOther = w.receivableCents() - w.payableCents() + stockDebt - investorsReceivable;
        raw.add(new CapitalResponses.Holder(ownerId, owner.map(PanelUser::getFullName).orElse("Dükkân sahibi"), true,
                owner.map(PanelUser::getCapitalBase).orElse(CapitalBase.ALL), ownerCapital, ownerCash,
                ownerInVehicles, ownerOther, 0, ownerVehicles, ownerId == null ? 0 : userBalance.getOrDefault(ownerId, 0L)));

        long allVehicles = raw.stream().mapToLong(CapitalResponses.Holder::vehicleCents).sum();
        return raw.stream()
                .map(h -> new CapitalResponses.Holder(h.userId(), h.name(), h.owner(), h.base(), h.capitalCents(), h.cashCents(),
                        h.vehicleCents(), h.otherCents(), switch (h.base()) {
                            case ALL -> MoneyMath.basisPoints(Math.max(0, h.capitalCents()), w.totalCents());
                            case VEHICLES -> MoneyMath.basisPoints(Math.max(0, h.vehicleCents()), allVehicles);
                            case CASH -> MoneyMath.basisPoints(Math.max(0, h.cashCents()), money);
                        }, h.vehicles(), h.balanceCents()))
                .sorted(Comparator.comparing(CapitalResponses.Holder::owner).reversed()
                        .thenComparing(Comparator.comparingLong(CapitalResponses.Holder::capitalCents).reversed()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AccountHoldings> holdings() {
        UUID tenant = CurrentUser.tenantId();
        List<Account> money = accounts.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenant, List.of(AccountType.CASH, AccountType.BANK));
        if (money.isEmpty()) return List.of();
        List<CapitalResponses.Holder> people = allHolders();
        Map<UUID, CapitalResponses.Holder> investors = new LinkedHashMap<>();
        CapitalResponses.Holder owner = null;
        for (CapitalResponses.Holder h : people) {
            if (h.owner()) owner = h;
            else if (h.userId() != null) investors.put(h.userId(), h);
        }

        // Kasa / bankaya dokunan hareketler zaman sirasiyla
        Map<UUID, LedgerEntry> touching = new LinkedHashMap<>();
        for (AccountType t : List.of(AccountType.CASH, AccountType.BANK)) {
            for (LedgerEntry e : entries.touchingType(tenant, t)) touching.put(e.getId(), e);
        }
        List<LedgerEntry> ordered = new ArrayList<>(touching.values());
        ordered.sort(Comparator.comparing(LedgerEntry::getEntryDate)
                .thenComparing(LedgerEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LedgerEntry::getId));

        Map<UUID, Map<UUID, Long>> shares = coOwnership.moneyEntryShares(tenant);
        // Sermayedarin evraki tahsil edildi: tahsilatin girdigi hesapta onun parasi
        for (Installment i : installments.findAllWithPlan(tenant)) {
            UUID holder = i.getPlan().getHolderUserId();
            if (holder == null || i.getPaidEntryId() == null || i.getHolderEntryId() == null) continue;
            shares.computeIfAbsent(i.getPaidEntryId(), k -> new HashMap<>()).merge(holder, i.getAmountCents(), Long::sum);
        }

        Map<UUID, Long> balance = new HashMap<>();
        Map<UUID, Map<UUID, Long>> held = new HashMap<>();
        for (Account a : money) held.put(a.getId(), new HashMap<>());
        for (LedgerEntry e : ordered) {
            Account src = e.getSourceAccount(), tgt = e.getTargetAccount();
            boolean srcMoney = held.containsKey(src.getId()), tgtMoney = held.containsKey(tgt.getId());
            long amount = e.getAmountCents();
            if (srcMoney && tgtMoney) {
                // Hesaplar arasi transfer: kaynaktaki herkesin parasi oraninda tasinir
                long before = balance.getOrDefault(src.getId(), 0L);
                if (before > 0) {
                    double f = Math.min(1.0, (double) amount / before);
                    for (Map.Entry<UUID, Long> p : held.get(src.getId()).entrySet()) {
                        long moved = Math.round(p.getValue() * f);
                        p.setValue(p.getValue() - moved);
                        held.get(tgt.getId()).merge(p.getKey(), moved, Long::sum);
                    }
                }
                balance.merge(src.getId(), -amount, Long::sum);
                balance.merge(tgt.getId(), amount, Long::sum);
                continue;
            }
            UUID account = srcMoney ? src.getId() : tgt.getId();
            long sign = tgtMoney ? 1 : -1;
            balance.merge(account, sign * amount, Long::sum);
            Account other = srcMoney ? tgt : src;
            Map<UUID, Long> here = held.get(account);
            if (other.getType() == AccountType.CAPITAL && other.getUserId() != null && investors.containsKey(other.getUserId())) {
                here.merge(other.getUserId(), sign * amount, Long::sum);   // sermaye girisi / cekisi, cepten katki, ortaga donus
            }
            Map<UUID, Long> parts = shares.get(e.getId());
            if (parts != null) parts.forEach((user, cents) -> { if (investors.containsKey(user)) here.merge(user, cents, Long::sum); });
        }

        // Izlenemeyen kisim (sermayeye eklenen kar payi, sermaye devri ...): hesaplara bakiyeleri oraninda
        List<Long> weights = money.stream().map(a -> Math.max(0, balance.getOrDefault(a.getId(), 0L))).toList();
        boolean anyPositive = weights.stream().anyMatch(w -> w > 0);
        for (CapitalResponses.Holder h : investors.values()) {
            long tracked = held.values().stream().mapToLong(m -> m.getOrDefault(h.userId(), 0L)).sum();
            long rest = h.cashCents() - tracked;
            if (rest == 0) continue;
            List<Long> split = anyPositive ? MoneyMath.splitByWeights(rest, weights)
                    : java.util.stream.IntStream.range(0, money.size()).mapToObj(k -> k == 0 ? rest : 0L).toList();
            for (int k = 0; k < money.size(); k++) held.get(money.get(k).getId()).merge(h.userId(), split.get(k), Long::sum);
        }

        List<AccountHoldings> result = new ArrayList<>();
        for (Account a : money) {
            long bal = balance.getOrDefault(a.getId(), 0L);
            List<AccountHoldings.Part> parts = new ArrayList<>();
            long investorsHere = 0;
            for (CapitalResponses.Holder h : investors.values()) {
                long cents = held.get(a.getId()).getOrDefault(h.userId(), 0L);
                if (cents == 0) continue;
                parts.add(new AccountHoldings.Part(h.userId(), h.name(), false, cents));
                investorsHere += cents;
            }
            parts.addFirst(new AccountHoldings.Part(owner == null ? null : owner.userId(), owner == null ? "Dükkân sahibi" : owner.name(), true,
                    bal - investorsHere));
            result.add(new AccountHoldings(a.getId(), a.getName(), a.getType(), bal, parts));
        }
        if (!CurrentUser.isInvestor()) return result;
        // Sermayedar hesaplarin bakiyesini ve baskalarinin parasini gormez: sadece kendi payi
        UUID me = CurrentUser.principal().userId();
        return result.stream().map(h -> {
            List<AccountHoldings.Part> mine = h.parts().stream().filter(p -> me.equals(p.userId()) && !p.owner()).toList();
            long cents = mine.stream().mapToLong(AccountHoldings.Part::cents).sum();
            return new AccountHoldings(h.accountId(), h.name(), h.type(), cents, mine);
        }).toList();
    }

    /** Stoktaki araclarda secilen sahiplerin odenmis katkisi, buyukten kucuge. */
    private static List<CapitalResponses.HolderVehicle> vehiclesOf(NetWorthResponse w, java.util.function.Predicate<NetWorthResponse.Owner> who) {
        Map<UUID, CapitalResponses.HolderVehicle> byVehicle = new LinkedHashMap<>();
        for (NetWorthResponse.StockVehicle v : w.vehicles()) {
            long cents = v.owners().stream().filter(who).mapToLong(NetWorthResponse.Owner::contributionCents).sum();
            if (cents > 0) byVehicle.put(v.vehicle().id(), new CapitalResponses.HolderVehicle(v.vehicle(), cents));
        }
        return byVehicle.values().stream().sorted(Comparator.comparingLong(CapitalResponses.HolderVehicle::cents).reversed()).toList();
    }

    /** Dukkan sahibinin arac payi: stok net degerinden diger sermayedarlarin odenmis katkisi dusulur. */
    private static List<CapitalResponses.HolderVehicle> ownerVehicles(NetWorthResponse w, UUID ownerId) {   // ownerId: bilgi
        return w.vehicles().stream().map(v -> {
            long investorContributions = v.owners().stream()
                    .filter(o -> !o.owner())
                    .mapToLong(NetWorthResponse.Owner::contributionCents)
                    .sum();
            long cents = v.costCents() - v.debtCents() - investorContributions;
            return new CapitalResponses.HolderVehicle(v.vehicle(), cents);
        }).filter(v -> v.cents() != 0)
                .sorted(Comparator.comparingLong(CapitalResponses.HolderVehicle::cents).reversed())
                .toList();
    }
}
