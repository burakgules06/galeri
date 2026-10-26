package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Account;
import com.galeri.app.model.enums.*;
import com.galeri.app.model.response.*;
import com.galeri.app.repository.*;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.*;
import com.galeri.app.util.CoOwnershipMath;
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
                shares.add(new NetWorthResponse.Owner(null, "Havuz", 10000, cost, debt, cost - debt, cost - debt));
            } else {
                int pool = -1;
                for (int k = 0; k < owners.size(); k++) if (owners.get(k).contactId() == null) pool = k;
                List<Long> assets = CoOwnershipMath.split(cost, owners.stream().map(ShareResponse::shareBp).toList(), pool);
                List<Long> ownerDebts = CoOwnershipMath.split(debt, owners.stream().map(ShareResponse::shareBp).toList(), pool);
                for (int k = 0; k < owners.size(); k++) {
                    var o = owners.get(k);
                    long ownerDebt = ownerDebts.get(k);
                    shares.add(new NetWorthResponse.Owner(o.contactId(), o.name(), o.shareBp(), assets.get(k), ownerDebt,
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
}
