package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.util.ConsignmentMath;
import com.galeri.app.model.response.DashboardResponse;
import com.galeri.app.model.response.DashboardResponse.ContactBalance;
import com.galeri.app.model.response.DashboardResponse.MonthStat;
import com.galeri.app.model.response.DashboardResponse.PartnerShare;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    static final int MONTH_COUNT = 6;
    static final int TOP_BALANCES = 5;
    static final int WEEK_DAYS = 7;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final ContactRepository contactRepository;
    private final VehicleRepository vehicleRepository;
    private final PaymentPlanService paymentPlanService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        UUID tenantId = CurrentUser.tenantId();
        LocalDate today = LocalDate.now(clock.withZone(VehicleServiceImpl.DEALER_ZONE));

        // ---- hesap bakiyeleri (giren - cikan)
        Map<UUID, Long> balances = new HashMap<>();
        for (Object[] s : entryRepository.incomingTotals(tenantId)) balances.merge((UUID) s[0], ((Number) s[1]).longValue(), Long::sum);
        for (Object[] s : entryRepository.outgoingTotals(tenantId)) balances.merge((UUID) s[0], -((Number) s[1]).longValue(), Long::sum);

        List<Account> accounts = accountRepository.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenantId,
                List.of(AccountType.CASH, AccountType.BANK, AccountType.CAPITAL, AccountType.BALANCE));
        Map<UUID, Contact> contacts = contactRepository.findAllById(accounts.stream()
                        .map(Account::getContactId).filter(id -> id != null).distinct().toList()).stream()
                .collect(Collectors.toMap(Contact::getId, Function.identity()));

        long cash = 0, bank = 0;
        List<PartnerShare> partners = new ArrayList<>();
        List<ContactBalance> receivables = new ArrayList<>();
        List<ContactBalance> payables = new ArrayList<>();
        for (Account a : accounts) {
            long balance = balances.getOrDefault(a.getId(), 0L);
            Contact c = a.getContactId() == null ? null : contacts.get(a.getContactId());
            switch (a.getType()) {
                case CASH -> cash += balance;
                case BANK -> bank += balance;
                // Sermaye hesabindan kasaya para cikar: ortagin sermayesi = hesabin eksi bakiyesi
                case CAPITAL -> {
                    if (-balance > 0) partners.add(new PartnerShare(a.getContactId(), name(c, a), c != null && c.isOwner(), -balance, 0));
                }
                case BALANCE -> {
                    if (balance > 0) receivables.add(new ContactBalance(a.getContactId(), name(c, a), phone(c), balance));
                    if (balance < 0) payables.add(new ContactBalance(a.getContactId(), name(c, a), phone(c), -balance));
                }
                default -> { }
            }
        }

        long capital = partners.stream().mapToLong(PartnerShare::capitalCents).sum();
        List<PartnerShare> shares = partners.stream()
                .map(p -> new PartnerShare(p.contactId(), p.name(), p.owner(), p.capitalCents(),
                        capital == 0 ? 0 : (int) Math.round(p.capitalCents() * 10000.0 / capital)))
                .sorted(Comparator.comparing(PartnerShare::owner).reversed()
                        .thenComparing(Comparator.comparingLong(PartnerShare::capitalCents).reversed()))
                .toList();

        long receivable = receivables.stream().mapToLong(ContactBalance::amountCents).sum();
        long payable = payables.stream().mapToLong(ContactBalance::amountCents).sum();

        // ---- stok ve aylar
        Map<UUID, Long> expenses = new HashMap<>();
        for (Object[] s : entryRepository.vehicleExpenseTotals(tenantId, LedgerEntryType.VEHICLE_EXPENSE, AccountType.VEHICLE)) {
            expenses.put((UUID) s[0], ((Number) s[1]).longValue());
        }
        // Konsinye araclar galerinin mali degil: stok sayisina ve maliyetine girmez, ayri sayilir
        List<Vehicle> allInStock = vehicleRepository.findByTenantIdAndStatus(tenantId, VehicleStatus.IN_STOCK);
        List<Vehicle> inStock = allInStock.stream().filter(v -> v.getOwnership() != Ownership.CONSIGNMENT).toList();
        int consignmentCount = allInStock.size() - inStock.size();
        long stockCost = inStock.stream().mapToLong(v -> cost(v, expenses)).sum();
        long averageDays = inStock.isEmpty() ? 0
                : Math.round(inStock.stream().mapToLong(v -> VehicleServiceImpl.daysInStock(v, today)).average().orElse(0));

        // ---- taksitler: geciken tahsilatlar ve bu hafta (bugun + 6 gun)
        List<InstallmentResponse> open = paymentPlanService.list(null, null, null, null).stream()
                .filter(i -> i.status() != InstallmentStatus.PAID).toList();
        List<InstallmentResponse> overdue = open.stream()
                .filter(i -> i.status() == InstallmentStatus.OVERDUE && i.direction() == PlanDirection.SALE).toList();
        LocalDate weekEnd = today.plusDays(WEEK_DAYS - 1);
        List<InstallmentResponse> week = open.stream()
                .filter(i -> !i.dueDate().isBefore(today) && !i.dueDate().isAfter(weekEnd)).toList();

        return new DashboardResponse(cash, bank, capital, receivable, payable, shares,
                top(receivables), top(payables), inStock.size(), consignmentCount, stockCost, averageDays,
                months(tenantId, YearMonth.from(today), expenses),
                overdue.size(), sum(overdue, null), week, sum(week, PlanDirection.SALE), sum(week, PlanDirection.PURCHASE));
    }

    private static long sum(List<InstallmentResponse> list, PlanDirection direction) {
        return list.stream().filter(i -> direction == null || i.direction() == direction)
                .mapToLong(InstallmentResponse::amountCents).sum();
    }

    private List<MonthStat> months(UUID tenantId, YearMonth current, Map<UUID, Long> expenses) {
        YearMonth first = current.minusMonths(MONTH_COUNT - 1);
        LocalDate start = first.atDay(1);
        LocalDate end = current.atEndOfMonth();
        Map<YearMonth, List<Vehicle>> bought = vehicleRepository.findByTenantIdAndPurchaseDateBetween(tenantId, start, end).stream()
                .collect(Collectors.groupingBy(v -> YearMonth.from(v.getPurchaseDate())));
        Map<YearMonth, List<Vehicle>> sold = vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, start, end).stream()
                .collect(Collectors.groupingBy(v -> YearMonth.from(v.getSaleDate())));

        List<MonthStat> result = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
            List<Vehicle> s = sold.getOrDefault(m, List.of());
            long revenue = s.stream().mapToLong(v -> v.getSalePriceCents() == null ? 0 : v.getSalePriceCents()).sum();
            // Arac kari galerinin (ve ortakli) araclarindan; konsinyeden gelen komisyon ayri satir (MIMARI §13 acik soru)
            long profit = s.stream().filter(v -> v.getSalePriceCents() != null && v.getOwnership() != Ownership.CONSIGNMENT)
                    .mapToLong(v -> v.getSalePriceCents() - cost(v, expenses)).sum();
            long commission = s.stream().filter(v -> v.getOwnership() == Ownership.CONSIGNMENT)
                    .mapToLong(v -> {
                        Long c = ConsignmentMath.commission(v, v.getSalePriceCents());
                        return c == null ? 0 : c;
                    }).sum();
            result.add(new MonthStat(m.getYear(), m.getMonthValue(), bought.getOrDefault(m, List.of()).size(), s.size(), revenue,
                    profit, commission));
        }
        return result;
    }

    /** Maliyet = gercek alis + defterdeki arac masraflari. */
    private static long cost(Vehicle v, Map<UUID, Long> expenses) {
        long purchase = v.getActualPurchaseCents() == null ? 0 : v.getActualPurchaseCents();
        return purchase + expenses.getOrDefault(v.getId(), 0L);
    }

    private static List<ContactBalance> top(List<ContactBalance> list) {
        return list.stream().sorted(Comparator.comparingLong(ContactBalance::amountCents).reversed()).limit(TOP_BALANCES).toList();
    }

    private static String name(Contact c, Account a) {
        return c != null ? c.getName() : a.getName();
    }

    private static String phone(Contact c) {
        return c == null ? null : c.getPhone();
    }
}
