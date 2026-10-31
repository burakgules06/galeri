package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Account;
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
        long total = cash + bank + stockTotal + receivable - payable;
        return new NetWorthResponse(cash, bank, stockTotal, receivable, payable, total, cap, opening, profit,
                total - cap - opening - profit, capital.partners(), stock);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CapitalResponses.Holder> holders() {
        UUID tenant = CurrentUser.tenantId();
        NetWorthResponse w = summary();
        Optional<PanelUser> owner = users.findPoolOwner(tenant);
        UUID ownerId = owner.map(PanelUser::getId).orElse(null);
        long money = w.cashCents() + w.bankCents();

        // Sermayedara ait tahsil edilmemis evrak: onun alacagi (riski onda), dukkan sahibinin payinda sayilmaz
        Map<UUID, Long> pending = new java.util.HashMap<>();
        for (Object[] row : installments.pendingByHolder(tenant)) pending.put((UUID) row[0], ((Number) row[1]).longValue());

        List<CapitalResponses.Holder> raw = new ArrayList<>();
        long investorsCapital = 0, investorsCash = 0;
        long investorsReceivable = 0;
        for (CapitalResponses.Partner p : w.partners()) {
            if (p.owner()) continue;   // dukkan sahipleri havuzun kalani
            List<CapitalResponses.HolderVehicle> list = vehiclesOf(w, o -> p.userId().equals(o.userId()));
            CapitalBase base = users.findById(p.userId()).map(PanelUser::getCapitalBase).orElse(CapitalBase.ALL);
            long receivable = pending.getOrDefault(p.userId(), 0L);
            raw.add(new CapitalResponses.Holder(p.userId(), p.name(), false, base, p.capitalCents() + receivable, p.cashCents(),
                    p.boundCents(), receivable, 0, list));
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
                ownerInVehicles, ownerOther, 0, ownerVehicles));

        long allVehicles = raw.stream().mapToLong(CapitalResponses.Holder::vehicleCents).sum();
        return raw.stream()
                .map(h -> new CapitalResponses.Holder(h.userId(), h.name(), h.owner(), h.base(), h.capitalCents(), h.cashCents(),
                        h.vehicleCents(), h.otherCents(), switch (h.base()) {
                            case ALL -> MoneyMath.basisPoints(Math.max(0, h.capitalCents()), w.totalCents());
                            case VEHICLES -> MoneyMath.basisPoints(Math.max(0, h.vehicleCents()), allVehicles);
                            case CASH -> MoneyMath.basisPoints(Math.max(0, h.cashCents()), money);
                        }, h.vehicles()))
                .sorted(Comparator.comparing(CapitalResponses.Holder::owner).reversed()
                        .thenComparing(Comparator.comparingLong(CapitalResponses.Holder::capitalCents).reversed()))
                .toList();
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
