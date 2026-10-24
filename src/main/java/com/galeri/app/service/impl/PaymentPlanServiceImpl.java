package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InstallmentAlreadyPaidException;
import com.galeri.app.exception.runtime.InstallmentNotPaidException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidPayerException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SaleHasPaymentsException;
import com.galeri.app.exception.runtime.SaleTotalMismatchException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PaymentPlan;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.InstallmentUpdateRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.ContactStatement;
import com.galeri.app.model.response.ContactSummary;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.model.response.VehicleRef;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.PaymentPlanRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentPlanServiceImpl implements PaymentPlanService {

    static final int STATEMENT_LIMIT = 1000;

    private final PaymentPlanRepository planRepository;
    private final InstallmentRepository installmentRepository;
    private final ContactRepository contactRepository;
    private final VehicleRepository vehicleRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final PanelUserRepository panelUserRepository;
    private final LedgerService ledgerService;
    private final Clock clock;

    // ------------------------------------------------------------------ satis

    @Override
    @Transactional
    public void recordSale(Vehicle vehicle, Contact buyer, VehicleSaleRequest r) {
        long total = r.installments().stream().mapToLong(InstallmentRequest::amountCents).sum();
        if (total != r.salePriceCents()) throw new SaleTotalMismatchException();
        UUID tenantId = vehicle.getTenantId();

        PaymentPlan plan = new PaymentPlan();
        plan.setTenantId(tenantId);
        plan.setVehicleId(vehicle.getId());
        plan.setDirection(PlanDirection.SALE);
        plan.setContact(buyer);
        plan.setTotalCents(total);
        plan.setCreatedBy(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
        plan = planRepository.save(plan);

        // Vadeye gore sirala (ayni vadede girilis sirasi korunur); odeyenleri once dogrula ki yarim kayit kalmasin.
        List<InstallmentRequest> rows = new ArrayList<>(r.installments());
        rows.sort(Comparator.comparing(InstallmentRequest::dueDate));
        List<Contact> payers = new ArrayList<>();
        for (InstallmentRequest row : rows) {
            payers.add(payer(row, buyer, tenantId));
            if (row.paidNow() && row.accountId() == null) throw new InvalidAccountException();
        }

        // Satis hareketi odeyen basina: Evim / kredi kismi alicinin degil kurumun carisine yazilir.
        Map<UUID, Long> perPayer = new LinkedHashMap<>();
        Map<UUID, Contact> payerById = new LinkedHashMap<>();
        for (int k = 0; k < rows.size(); k++) {
            perPayer.merge(payers.get(k).getId(), rows.get(k).amountCents(), Long::sum);
            payerById.putIfAbsent(payers.get(k).getId(), payers.get(k));
        }
        perPayer.forEach((id, amount) -> ledgerService.writeSale(vehicle, payerById.get(id), amount, r.saleDate()));

        for (int k = 0; k < rows.size(); k++) {
            InstallmentRequest row = rows.get(k);
            Installment i = new Installment();
            i.setTenantId(tenantId);
            i.setPlan(plan);
            i.setSeq((short) (k + 1));
            i.setDueDate(row.dueDate());
            i.setAmountCents(row.amountCents());
            i.setMethod(row.method());
            i.setNoteNo(blankToNull(row.noteNo()));
            i.setPayer(payers.get(k));
            if (row.paidNow()) {
                LedgerEntry paid = ledgerService.writeSettlement(PlanDirection.SALE, vehicle, payers.get(k), row.accountId(),
                        row.amountCents(), r.saleDate(), rows.size() == 1 ? "Peşin tahsilat" : "Peşinat");
                i.setPaidEntryId(paid.getId());
                i.setPaidAt(now());
            }
            installmentRepository.save(i);
        }
    }

    @Override
    @Transactional
    public void undoSale(Vehicle vehicle) {
        planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.SALE).ifPresent(plan -> {
            if (installmentRepository.existsByPlan_IdAndPaidEntryIdIsNotNull(plan.getId())) throw new SaleHasPaymentsException();
            installmentRepository.deleteAll(installmentRepository.findByPlanIds(List.of(plan.getId())));
            planRepository.delete(plan);
        });
        ledgerService.reverseVehicleSales(vehicle.getId());
    }

    // ------------------------------------------------------------------ okuma

    @Override
    @Transactional(readOnly = true)
    public List<PaymentPlanResponse> plans(UUID vehicleId) {
        Vehicle vehicle = getVehicle(vehicleId);
        List<PaymentPlan> plans = planRepository.findByVehicleIdOrderByDirection(vehicle.getId());
        if (plans.isEmpty()) return List.of();
        Map<UUID, List<Installment>> byPlan = installmentRepository.findByPlanIds(plans.stream().map(PaymentPlan::getId).toList())
                .stream().collect(Collectors.groupingBy(i -> i.getPlan().getId()));
        LocalDate today = today();
        VehicleRef ref = ref(vehicle);
        return plans.stream().map(p -> {
            List<InstallmentResponse> rows = byPlan.getOrDefault(p.getId(), List.of()).stream()
                    .map(i -> toResponse(i, ref, byPlan.get(p.getId()).size(), today)).toList();
            long paid = rows.stream().filter(i -> i.status() == InstallmentStatus.PAID).mapToLong(InstallmentResponse::amountCents).sum();
            long overdue = rows.stream().filter(i -> i.status() == InstallmentStatus.OVERDUE).mapToLong(InstallmentResponse::amountCents).sum();
            return new PaymentPlanResponse(p.getId(), p.getDirection(), p.getVehicleId(), summary(p.getContact()),
                    p.getTotalCents(), paid, p.getTotalCents() - paid, overdue, rows);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstallmentResponse> list(InstallmentStatus status, PlanDirection direction, LocalDate from, LocalDate to) {
        UUID tenantId = CurrentUser.tenantId();
        List<Installment> all = installmentRepository.findAllWithPlan(tenantId);
        if (all.isEmpty()) return List.of();
        Map<UUID, Long> counts = all.stream().collect(Collectors.groupingBy(i -> i.getPlan().getId(), Collectors.counting()));
        Map<UUID, VehicleRef> vehicles = vehicleRepository.findAllById(all.stream().map(i -> i.getPlan().getVehicleId()).distinct().toList())
                .stream().collect(Collectors.toMap(Vehicle::getId, PaymentPlanServiceImpl::ref));
        LocalDate today = today();
        return all.stream()
                .filter(i -> direction == null || i.getPlan().getDirection() == direction)
                .filter(i -> from == null || !i.getDueDate().isBefore(from))
                .filter(i -> to == null || !i.getDueDate().isAfter(to))
                .map(i -> toResponse(i, vehicles.get(i.getPlan().getVehicleId()), counts.get(i.getPlan().getId()).intValue(), today))
                .filter(i -> status == null || i.status() == status)
                .toList();
    }

    // ------------------------------------------------------------------ tahsilat

    @Override
    @Transactional
    public InstallmentResponse collect(UUID installmentId, CollectRequest r) {
        Installment i = getInstallment(installmentId);
        if (i.isPaid()) throw new InstallmentAlreadyPaidException();
        PaymentPlan plan = i.getPlan();
        Vehicle vehicle = vehicleRepository.findById(plan.getVehicleId()).orElseThrow(RecordNotFoundException::new);
        LedgerEntry entry = ledgerService.writeSettlement(plan.getDirection(), vehicle, i.getPayer(), r.accountId(),
                i.getAmountCents(), r.date(), description(plan.getDirection(), i.getMethod()));
        i.setPaidEntryId(entry.getId());
        i.setPaidAt(now());
        return single(i, vehicle);
    }

    @Override
    @Transactional
    public InstallmentResponse undo(UUID installmentId) {
        Installment i = getInstallment(installmentId);
        if (!i.isPaid()) throw new InstallmentNotPaidException();
        ledgerService.reverse(i.getPaidEntryId());
        i.setPaidEntryId(null);
        i.setPaidAt(null);
        return single(i, null);
    }

    @Override
    @Transactional
    public InstallmentResponse update(UUID installmentId, InstallmentUpdateRequest r) {
        Installment i = getInstallment(installmentId);
        if (i.isPaid()) throw new InstallmentAlreadyPaidException();
        i.setDueDate(r.dueDate());
        i.setNoteNo(blankToNull(r.noteNo()));
        return single(i, null);
    }

    // ------------------------------------------------------------------ cari

    @Override
    @Transactional(readOnly = true)
    public ContactStatement statement(UUID contactId) {
        UUID tenantId = CurrentUser.tenantId();
        Contact contact = getContact(contactId, tenantId);
        Optional<Account> account = accountRepository.findFirstByTenantIdAndTypeAndContactId(tenantId, AccountType.BALANCE, contact.getId());

        List<ContactStatement.Line> lines = new ArrayList<>();
        long running = 0;
        if (account.isPresent()) {
            UUID accountId = account.get().getId();
            List<LedgerEntry> entries = new ArrayList<>(entryRepository.accountEntries(tenantId, accountId, PageRequest.of(0, STATEMENT_LIMIT)));
            Collections.reverse(entries);   // sorgu yeniden eskiye doner; ozet eskiden yeniye yurur
            for (LedgerEntry e : entries) {
                // Cariye giren (alicinin borcu) arti, cariden cikan (tahsilat ya da bizim borcumuz) eksi
                long amount = e.getTargetAccount().getId().equals(accountId) ? e.getAmountCents() : -e.getAmountCents();
                running += amount;
                lines.add(new ContactStatement.Line(e.getId(), e.getEntryDate(), e.getType(), e.getDescription(),
                        e.getVehicle() == null ? null : ref(e.getVehicle()), amount, running, e.getCorrectedEntryId() != null));
            }
        }
        List<InstallmentResponse> open = list(null, null, null, null).stream()
                .filter(i -> i.status() != InstallmentStatus.PAID && i.payer().id().equals(contact.getId()))
                .toList();
        return new ContactStatement(ContactServiceImpl.toResponse(contact), running, lines, open);
    }

    // ------------------------------------------------------------------ yardimcilar

    /** Odeyen: kurum taksitinde secilen kurum (zorunlu), digerlerinde secilen kisi ya da alici. */
    private Contact payer(InstallmentRequest row, Contact buyer, UUID tenantId) {
        if (row.method() == PaymentMethod.INSTITUTION) {
            if (row.payerId() == null) throw new InvalidPayerException();
            Contact c = getContact(row.payerId(), tenantId);
            if (c.getType() != ContactType.ORGANIZATION) throw new InvalidPayerException();
            return c;
        }
        return row.payerId() == null ? buyer : getContact(row.payerId(), tenantId);
    }

    static String description(PlanDirection direction, PaymentMethod method) {
        if (direction == PlanDirection.PURCHASE) return method == PaymentMethod.PROMISSORY_NOTE ? "Senet ödemesi" : "Ödeme";
        return switch (method) {
            case PROMISSORY_NOTE -> "Senet tahsilatı";
            case INSTITUTION -> "Kurum ödemesi";
            default -> "Tahsilat";
        };
    }

    private InstallmentResponse single(Installment i, Vehicle vehicle) {
        Vehicle v = vehicle != null ? vehicle
                : vehicleRepository.findById(i.getPlan().getVehicleId()).orElseThrow(RecordNotFoundException::new);
        int count = installmentRepository.findByPlanIds(List.of(i.getPlan().getId())).size();
        return toResponse(i, ref(v), count, today());
    }

    private static InstallmentResponse toResponse(Installment i, VehicleRef vehicle, int count, LocalDate today) {
        InstallmentStatus status = i.isPaid() ? InstallmentStatus.PAID
                : i.getDueDate().isBefore(today) ? InstallmentStatus.OVERDUE : InstallmentStatus.UPCOMING;
        long daysLate = status == InstallmentStatus.OVERDUE ? ChronoUnit.DAYS.between(i.getDueDate(), today) : 0;
        PaymentPlan p = i.getPlan();
        return new InstallmentResponse(i.getId(), p.getId(), p.getDirection(), vehicle, i.getSeq(), count, i.getDueDate(),
                i.getAmountCents(), i.getMethod(), i.getNoteNo(), summary(i.getPayer()), summary(p.getContact()),
                status, daysLate, i.getPaidEntryId(), i.getPaidAt());
    }

    private Installment getInstallment(UUID id) {
        Installment i = installmentRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!i.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return i;
    }

    private Vehicle getVehicle(UUID id) {
        Vehicle v = vehicleRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!v.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return v;
    }

    private Contact getContact(UUID id, UUID tenantId) {
        Contact c = contactRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!c.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        return c;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(VehicleServiceImpl.DEALER_ZONE));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock.withZone(VehicleServiceImpl.DEALER_ZONE));
    }

    private static VehicleRef ref(Vehicle v) {
        return new VehicleRef(v.getId(), v.getPlate(), v.getMake(), v.getModel());
    }

    private static ContactSummary summary(Contact c) {
        return c == null ? null : new ContactSummary(c.getId(), c.getName(), c.getPhone());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
