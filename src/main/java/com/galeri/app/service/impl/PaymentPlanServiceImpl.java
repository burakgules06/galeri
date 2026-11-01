package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.BounceNotAllowedException;
import com.galeri.app.exception.runtime.CapitalExceededException;
import com.galeri.app.exception.runtime.EndorseNotAllowedException;
import com.galeri.app.exception.runtime.InvalidEndorseTargetException;
import com.galeri.app.exception.runtime.InvalidPartialAmountException;
import com.galeri.app.exception.runtime.InstallmentAlreadyPaidException;
import com.galeri.app.exception.runtime.InstallmentNotPaidException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidPayerException;
import com.galeri.app.exception.runtime.InvalidOpenAccountUsageException;
import com.galeri.app.exception.runtime.PlanNotRemovableException;
import com.galeri.app.exception.runtime.NotAPartnerException;
import com.galeri.app.exception.runtime.PurchaseHasPaymentsException;
import com.galeri.app.exception.runtime.VehicleNotRemovableException;
import com.galeri.app.exception.runtime.PurchaseTotalMismatchException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SaleHasPaymentsException;
import com.galeri.app.exception.runtime.SaleTotalMismatchException;
import com.galeri.app.exception.runtime.SellerRequiredException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.PaymentPlan;
import com.galeri.app.model.entity.PaymentPlanVehicle;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.BasketSaleRequest;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.EndorseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.InstallmentUpdateRequest;
import com.galeri.app.model.request.OpenItemsRequest;
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
import com.galeri.app.repository.PaymentPlanVehicleRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.service.CoOwnershipService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
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
    private final CoOwnershipService coOwnershipService;
    private final Clock clock;
    private final PaymentPlanVehicleRepository basketRepository;

    // ------------------------------------------------------------------ satis

    @Override
    @Transactional
    public void recordSale(Vehicle vehicle, Contact buyer, VehicleSaleRequest r) {
        ensureNoOpenAccount(r.installments());
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
            if (row.paidNow() && row.accountId() == null) {
                throw new InvalidAccountException();
            }
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
            i.setBankName(blankToNull(row.bankName()));
            i.setDrawerName(blankToNull(row.drawerName()));
            i.setPayer(payers.get(k));
            if (row.paidNow()) {
                LedgerEntry paid = settle(i, PlanDirection.SALE, vehicle, row.accountId(), r.saleDate(),
                        rows.size() == 1 ? "Peşin tahsilat" : "Peşinat");
                i.setPaidEntryId(paid.getId());
                i.setPaidAt(now());
                i.setDownPayment(true);
            }
            installmentRepository.save(i);
        }
    }

    @Override
    @Transactional
    public void undoSale(Vehicle vehicle) {
        Optional<PaymentPlanVehicle> link = basketRepository.findByVehicleId(vehicle.getId());
        if (link.isPresent()) {
            undoBasket(link.get().getPlanId());
            return;
        }
        planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.SALE).ifPresent(plan -> {
            if (installmentRepository.existsByPlan_IdAndPaidEntryIdIsNotNull(plan.getId())) throw new SaleHasPaymentsException();
            installmentRepository.deleteAll(installmentRepository.findByPlanIds(List.of(plan.getId())));
            planRepository.delete(plan);
        });
        ledgerService.reverseVehicleSales(vehicle.getId());
    }

    // ------------------------------------------------------------------ toplu satis (sepet)

    @Override
    @Transactional
    public void recordBasketSale(List<Vehicle> vehicles, List<Long> prices, Contact buyer, BasketSaleRequest r) {
        ensureNoOpenAccount(r.installments());
        long total = prices.stream().mapToLong(Long::longValue).sum();
        if (r.installments().stream().mapToLong(InstallmentRequest::amountCents).sum() != total) throw new SaleTotalMismatchException();
        UUID tenantId = buyer.getTenantId();

        PaymentPlan plan = new PaymentPlan();
        plan.setTenantId(tenantId);
        plan.setDirection(PlanDirection.SALE);
        plan.setContact(buyer);
        plan.setTotalCents(total);
        plan.setCreatedBy(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
        plan = planRepository.save(plan);
        for (int k = 0; k < vehicles.size(); k++) {
            PaymentPlanVehicle link = new PaymentPlanVehicle();
            link.setTenantId(tenantId);
            link.setPlanId(plan.getId());
            link.setVehicleId(vehicles.get(k).getId());
            link.setSalePriceCents(prices.get(k));
            basketRepository.save(link);
        }

        List<InstallmentRequest> rows = new ArrayList<>(r.installments());
        rows.sort(Comparator.comparing(InstallmentRequest::dueDate));
        List<Contact> payers = new ArrayList<>();
        for (InstallmentRequest row : rows) {
            payers.add(payer(row, buyer, tenantId));
            if (row.paidNow() && row.accountId() == null) {
                throw new InvalidAccountException();
            }
        }

        // Satis hareketi arac ve odeyen basina: odeyenlerin tutarlari araclarin bedellerine sirayla dagitilir (selale).
        // Her aracin hareketleri toplami bedeline, her odeyeninki taksitlerinin toplamina kurusu kurusuna esit.
        Map<UUID, Long> perPayer = new LinkedHashMap<>();
        Map<UUID, Contact> payerById = new LinkedHashMap<>();
        for (int k = 0; k < rows.size(); k++) {
            perPayer.merge(payers.get(k).getId(), rows.get(k).amountCents(), Long::sum);
            payerById.putIfAbsent(payers.get(k).getId(), payers.get(k));
        }
        List<UUID> payerIds = new ArrayList<>(perPayer.keySet());
        int p = 0;
        long payerLeft = perPayer.get(payerIds.get(0));
        for (int k = 0; k < vehicles.size(); k++) {
            long need = prices.get(k);
            while (need > 0) {
                long take = Math.min(need, payerLeft);
                ledgerService.writeSale(vehicles.get(k), payerById.get(payerIds.get(p)), take, r.saleDate());
                need -= take;
                payerLeft -= take;
                if (payerLeft == 0 && p + 1 < payerIds.size()) payerLeft = perPayer.get(payerIds.get(++p));
            }
        }

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
            i.setBankName(blankToNull(row.bankName()));
            i.setDrawerName(blankToNull(row.drawerName()));
            i.setPayer(payers.get(k));
            if (row.paidNow()) {
                LedgerEntry paid = settle(i, PlanDirection.SALE, null, row.accountId(), r.saleDate(),
                        rows.size() == 1 ? "Peşin tahsilat" : "Peşinat");
                i.setPaidEntryId(paid.getId());
                i.setPaidAt(now());
                i.setDownPayment(true);
            }
            installmentRepository.save(i);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> basketVehicleIds(UUID vehicleId) {
        return basketRepository.findByVehicleId(vehicleId)
                .map(l -> basketRepository.findByPlanIdOrderByCreatedAtAscIdAsc(l.getPlanId()).stream().map(PaymentPlanVehicle::getVehicleId).toList())
                .orElse(List.of());
    }

    /** Sepet butunuyle geri alinir: hicbir taksiti odenmemis olmali; araclarin satis hareketleri terslenir, plan silinir. */
    private void undoBasket(UUID planId) {
        if (installmentRepository.existsByPlan_IdAndPaidEntryIdIsNotNull(planId)) throw new SaleHasPaymentsException();
        List<PaymentPlanVehicle> links = basketRepository.findByPlanIdOrderByCreatedAtAscIdAsc(planId);
        installmentRepository.deleteAll(installmentRepository.findByPlanIds(List.of(planId)));
        basketRepository.deleteAll(links);
        planRepository.deleteById(planId);
        links.forEach(l -> ledgerService.reverseVehicleSales(l.getVehicleId()));
    }

    // ------------------------------------------------------------------ alis

    @Override
    @Transactional
    public void syncPurchase(Vehicle vehicle, UUID purchaseAccountId, List<InstallmentRequest> rows) {
        if (rows != null) ensureNoOpenAccount(rows);
        Optional<PaymentPlan> existing = planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.PURCHASE);
        List<Installment> current = existing.map(p -> installmentRepository.findByPlanIds(List.of(p.getId()))).orElse(List.of());
        List<Installment> paid = current.stream().filter(Installment::isPaid).toList();

        // ---- pesin alis: plan varsa kaldir (odeme yapilmadiysa), sonra bugunku akis
        if (rows == null || rows.isEmpty()) {
            if (existing.isPresent()) {
                if (!paid.isEmpty()) throw new PurchaseHasPaymentsException();
                installmentRepository.deleteAll(current);
                planRepository.delete(existing.get());
                ledgerService.reverseVehiclePurchases(vehicle.getId());
            }
            ledgerService.syncVehiclePurchase(vehicle, purchaseAccountId);
            return;
        }

        // ---- planli alis: dogrulama
        Contact seller = vehicle.getSeller();
        if (seller == null) throw new SellerRequiredException();
        long price = vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents();
        long paidSum = paid.stream().mapToLong(Installment::getAmountCents).sum();
        long rowSum = rows.stream().mapToLong(InstallmentRequest::amountCents).sum();
        if (paidSum + rowSum != price) throw new PurchaseTotalMismatchException();
        if (!paid.isEmpty() && (existing.get().getTotalCents() != price || !existing.get().getContact().getId().equals(seller.getId()))) {
            throw new PurchaseHasPaymentsException();
        }
        List<InstallmentRequest> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(InstallmentRequest::dueDate));
        List<Contact> payees = new ArrayList<>();
        for (InstallmentRequest row : sorted) {
            payees.add(payer(row, seller, vehicle.getTenantId()));
            if (row.paidNow() && row.accountId() == null) throw new InvalidAccountException();
        }

        // ---- defter: odenecek kisi basina toplam, alis tarihinde. Ayniysa dokunma.
        Map<UUID, Long> wanted = new LinkedHashMap<>();
        Map<UUID, Contact> payeeById = new LinkedHashMap<>();
        for (Installment i : paid) {
            wanted.merge(i.getPayer().getId(), i.getAmountCents(), Long::sum);
            payeeById.putIfAbsent(i.getPayer().getId(), i.getPayer());
        }
        for (int k = 0; k < sorted.size(); k++) {
            wanted.merge(payees.get(k).getId(), sorted.get(k).amountCents(), Long::sum);
            payeeById.putIfAbsent(payees.get(k).getId(), payees.get(k));
        }
        if (!ledgerMatches(ledgerService.activeVehiclePurchases(vehicle.getId()), wanted, vehicle.getPurchaseDate())) {
            ledgerService.reverseVehiclePurchases(vehicle.getId());
            wanted.forEach((id, amount) -> ledgerService.writePlannedPurchase(vehicle, payeeById.get(id), amount, vehicle.getPurchaseDate()));
        }

        // ---- plan ve odenmemis taksitler: ayniysa dokunma, degilse odenmemisleri yeniden yaz
        List<Installment> unpaid = current.stream().filter(i -> !i.isPaid()).toList();
        if (existing.isPresent() && sameRows(unpaid, sorted, payees)) return;

        PaymentPlan plan = existing.orElseGet(() -> {
            PaymentPlan p = new PaymentPlan();
            p.setTenantId(vehicle.getTenantId());
            p.setVehicleId(vehicle.getId());
            p.setDirection(PlanDirection.PURCHASE);
            p.setCreatedBy(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
            return p;
        });
        plan.setContact(seller);
        plan.setTotalCents(price);
        plan = planRepository.save(plan);
        installmentRepository.deleteAll(unpaid);
        installmentRepository.flush();   // (plan_id, seq) tekilligi: silmeler eklemelerden once calissin

        int seq = paid.stream().mapToInt(Installment::getSeq).max().orElse(0);
        for (int k = 0; k < sorted.size(); k++) {
            InstallmentRequest row = sorted.get(k);
            Installment i = new Installment();
            i.setTenantId(vehicle.getTenantId());
            i.setPlan(plan);
            i.setSeq((short) ++seq);
            i.setDueDate(row.dueDate());
            i.setAmountCents(row.amountCents());
            i.setMethod(row.method());
            i.setNoteNo(blankToNull(row.noteNo()));
            i.setBankName(blankToNull(row.bankName()));
            i.setDrawerName(blankToNull(row.drawerName()));
            i.setPayer(payees.get(k));
            if (row.paidNow()) {
                LedgerEntry e = ledgerService.writeSettlement(PlanDirection.PURCHASE, vehicle, payees.get(k), row.accountId(),
                        row.amountCents(), vehicle.getPurchaseDate(), sorted.size() == 1 && paid.isEmpty() ? "Peşin ödeme" : "Peşinat");
                i.setPaidEntryId(e.getId());
                i.setPaidAt(now());
                i.setDownPayment(true);
            }
            installmentRepository.save(i);
        }
    }

    /** Gecerli alis hareketleri istenenle ayni mi: hepsi saticinin carisinden, alis tarihinde, kisi basina ayni tutar. */
    private static boolean ledgerMatches(List<LedgerEntry> active, Map<UUID, Long> wanted, LocalDate date) {
        Map<UUID, Long> actual = new LinkedHashMap<>();
        for (LedgerEntry e : active) {
            if (e.getSourceAccount().getType() != AccountType.BALANCE || e.getSourceAccount().getContactId() == null
                    || !e.getEntryDate().equals(date)) return false;
            actual.merge(e.getSourceAccount().getContactId(), e.getAmountCents(), Long::sum);
        }
        return actual.equals(wanted);
    }

    /** Odenmemis taksitler istenen satirlarla ayni mi (siraya gore vade, tutar, yontem, senet no, odenecek kisi). */
    private static boolean sameRows(List<Installment> unpaid, List<InstallmentRequest> rows, List<Contact> payees) {
        if (unpaid.size() != rows.size() || rows.stream().anyMatch(InstallmentRequest::paidNow)) return false;
        List<Installment> ordered = unpaid.stream()
                .sorted(Comparator.comparing(Installment::getDueDate).thenComparing(Installment::getSeq)).toList();
        for (int k = 0; k < rows.size(); k++) {
            Installment i = ordered.get(k);
            InstallmentRequest r = rows.get(k);
            if (!i.getDueDate().equals(r.dueDate()) || i.getAmountCents() != r.amountCents().longValue() || i.getMethod() != r.method()
                    || !Objects.equals(i.getNoteNo(), blankToNull(r.noteNo()))
                    || !Objects.equals(i.getBankName(), blankToNull(r.bankName()))
                    || !Objects.equals(i.getDrawerName(), blankToNull(r.drawerName()))
                    || !i.getPayer().getId().equals(payees.get(k).getId())) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ okuma

    @Override
    @Transactional(readOnly = true)
    public List<PaymentPlanResponse> plans(UUID vehicleId) {
        Vehicle vehicle = getVehicle(vehicleId);
        List<PaymentPlan> plans = new ArrayList<>(planRepository.findByVehicleIdOrderByDirection(vehicle.getId()));
        basketRepository.findByVehicleId(vehicle.getId()).flatMap(l -> planRepository.findById(l.getPlanId())).ifPresent(plans::add);
        if (plans.isEmpty()) return List.of();
        Map<UUID, List<Installment>> byPlan = installmentRepository.findByPlanIds(plans.stream().map(PaymentPlan::getId).toList())
                .stream().collect(Collectors.groupingBy(i -> i.getPlan().getId()));
        LocalDate today = today();
        VehicleRef ref = ref(vehicle);
        return plans.stream().map(p -> planResponse(p, byPlan.getOrDefault(p.getId(), List.of()), ref, today, basketItems(p))).toList();
    }

    /** Sepet planinin araclari ve bedelleri (normal planda bos). */
    private List<PaymentPlanResponse.BasketItem> basketItems(PaymentPlan p) {
        if (p.getVehicleId() != null || p.getOpeningEntryId() != null) return List.of();
        List<PaymentPlanVehicle> links = basketRepository.findByPlanIdOrderByCreatedAtAscIdAsc(p.getId());
        Map<UUID, Vehicle> vehicles = vehicleRepository.findAllById(links.stream().map(PaymentPlanVehicle::getVehicleId).toList()).stream()
                .collect(Collectors.toMap(Vehicle::getId, v -> v));
        return links.stream().map(l -> new PaymentPlanResponse.BasketItem(ref(vehicles.get(l.getVehicleId())), l.getSalePriceCents())).toList();
    }

    private static PaymentPlanResponse planResponse(PaymentPlan p, List<Installment> installments, VehicleRef ref, LocalDate today,
                                                    List<PaymentPlanResponse.BasketItem> basket) {
        List<InstallmentResponse> rows = installments.stream().map(i -> toResponse(i, ref, installments, today)).toList();
        long paid = rows.stream().filter(i -> i.status() == InstallmentStatus.PAID).mapToLong(InstallmentResponse::amountCents).sum();
        long overdue = rows.stream().filter(i -> i.status() == InstallmentStatus.OVERDUE).mapToLong(InstallmentResponse::amountCents).sum();
        return new PaymentPlanResponse(p.getId(), p.getDirection(), p.getVehicleId(), summary(p.getContact()),
                p.getTotalCents(), paid, p.getTotalCents() - paid, overdue, rows, basket);
    }

    // ------------------------------------------------------------------ gecmis senet / cek (aracsiz)

    @Override
    @Transactional
    public PaymentPlanResponse addOpenItems(UUID contactId, OpenItemsRequest r) {
        UUID tenantId = CurrentUser.tenantId();
        Contact contact = getContact(contactId, tenantId);
        UUID holder = null;
        if (r.holderId() != null && r.direction() == PlanDirection.SALE) {
            // Evrak sahibi sermayedar kullanici; dukkan sahibine ait olan zaten onun payinda
            PanelUser h = panelUserRepository.findById(r.holderId()).orElseThrow(RecordNotFoundException::new);
            if (h.getTenant() == null || !h.getTenant().getId().equals(tenantId)) throw new SourceNotBelongToTenantException();
            if (!"INVESTOR".equals(h.getRole())) throw new NotAPartnerException();
            holder = h.getId();
        }
        long total = r.installments().stream().mapToLong(InstallmentRequest::amountCents).sum();
        LedgerEntry opening = ledgerService.writeOpenItems(contact, r.direction(), total, r.date());

        PaymentPlan plan = new PaymentPlan();
        plan.setTenantId(tenantId);
        plan.setDirection(r.direction());
        plan.setContact(contact);
        plan.setTotalCents(total);
        plan.setOpeningEntryId(opening.getId());
        plan.setHolderUserId(holder);
        plan.setCreatedBy(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
        plan = planRepository.save(plan);

        List<InstallmentRequest> rows = new ArrayList<>(r.installments());
        rows.sort(Comparator.comparing(InstallmentRequest::dueDate));
        List<Installment> saved = new ArrayList<>();
        for (int k = 0; k < rows.size(); k++) {
            InstallmentRequest row = rows.get(k);
            Installment i = new Installment();
            i.setTenantId(tenantId);
            i.setPlan(plan);
            i.setSeq((short) (k + 1));
            i.setDueDate(row.dueDate());
            i.setAmountCents(row.amountCents());
            // Kurum yontemi aracsiz kayitta yok: odeyen her zaman kisinin kendisi
            i.setMethod(row.method() == PaymentMethod.INSTITUTION ? PaymentMethod.TRANSFER : row.method());
            i.setNoteNo(blankToNull(row.noteNo()));
            i.setDescription(blankToNull(row.description()));
            i.setBankName(blankToNull(row.bankName()));
            i.setDrawerName(blankToNull(row.drawerName()));
            i.setPayer(contact);
            saved.add(installmentRepository.save(i));
        }
        return planResponse(plan, saved, null, today(), List.of());
    }

    @Override
    @Transactional
    public void removeOpenItems(UUID planId) {
        PaymentPlan plan = planRepository.findById(planId).orElseThrow(RecordNotFoundException::new);
        if (!plan.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (plan.getOpeningEntryId() == null || installmentRepository.existsByPlan_IdAndPaidEntryIdIsNotNull(plan.getId())) {
            throw new PlanNotRemovableException();
        }
        ledgerService.reverse(plan.getOpeningEntryId());
        installmentRepository.deleteAll(installmentRepository.findByPlanIds(List.of(plan.getId())));
        planRepository.delete(plan);
    }

    @Override
    @Transactional
    public void removeOpenItemInstallment(UUID planId, UUID installmentId) {
        PaymentPlan plan = planRepository.lockById(planId).orElseThrow(RecordNotFoundException::new);
        if (!plan.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (plan.getOpeningEntryId() == null) throw new PlanNotRemovableException();
        List<Installment> rows = installmentRepository.lockByPlanId(planId);
        Installment target = rows.stream().filter(i -> i.getId().equals(installmentId)).findFirst()
                .orElseThrow(RecordNotFoundException::new);
        if (!target.getTenantId().equals(CurrentUser.tenantId()) || target.isPaid()) throw new PlanNotRemovableException();

        List<Installment> retained = rows.stream().filter(i -> !i.getId().equals(installmentId)).toList();
        long retainedTotal = retained.stream().mapToLong(Installment::getAmountCents).sum();
        LedgerEntry reversal = ledgerService.reverseAndGet(plan.getOpeningEntryId());
        installmentRepository.delete(target);
        if (retained.isEmpty()) {
            planRepository.delete(plan);
            return;
        }

        LedgerEntry revisedOpening = ledgerService.writeOpenItems(plan.getContact(), plan.getDirection(), retainedTotal, reversal.getEntryDate());
        entryRepository.flush();
        installmentRepository.flush();
        planRepository.replaceOpenItemsAmountAndOpeningEntry(planId, retainedTotal, revisedOpening.getId());
    }


    @Override
    @Transactional
    public void removePurchasePlan(Vehicle vehicle) {
        planRepository.findByVehicleIdAndDirection(vehicle.getId(), PlanDirection.PURCHASE).ifPresent(plan -> {
            List<Installment> rows = installmentRepository.findByPlanIds(List.of(plan.getId()));
            if (rows.stream().anyMatch(i -> i.isPaid() && !i.isDownPayment())) {
                throw new VehicleNotRemovableException("Ödenmiş alış senedi / taksiti var. Önce ödemeyi geri alın.");
            }
            installmentRepository.deleteAll(rows);
            planRepository.delete(plan);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstallmentResponse> list(InstallmentStatus status, PlanDirection direction, LocalDate from, LocalDate to) {
        UUID tenantId = CurrentUser.tenantId();
        List<Installment> all = installmentRepository.findAllWithPlan(tenantId);
        if (all.isEmpty()) return List.of();
        Map<UUID, List<Installment>> byPlan = all.stream().collect(Collectors.groupingBy(i -> i.getPlan().getId()));
        // Sepet planinda listede ilk arac gosterilir
        Map<UUID, UUID> basketFirst = new HashMap<>();
        List<UUID> vehicleless = all.stream().map(Installment::getPlan).filter(p -> p.getVehicleId() == null && p.getOpeningEntryId() == null)
                .map(PaymentPlan::getId).distinct().toList();
        if (!vehicleless.isEmpty()) {
            basketRepository.findByPlanIdIn(vehicleless).stream()
                    .sorted(Comparator.comparing(PaymentPlanVehicle::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(l -> basketFirst.putIfAbsent(l.getPlanId(), l.getVehicleId()));
        }
        Function<PaymentPlan, UUID> vehicleIdOf = p -> p.getVehicleId() != null ? p.getVehicleId() : basketFirst.get(p.getId());
        Map<UUID, VehicleRef> vehicles = vehicleRepository.findAllById(all.stream().map(i -> vehicleIdOf.apply(i.getPlan()))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Vehicle::getId, PaymentPlanServiceImpl::ref));
        LocalDate today = today();
        return all.stream()
                .filter(i -> direction == null || i.getPlan().getDirection() == direction)
                .filter(i -> from == null || !i.getDueDate().isBefore(from))
                .filter(i -> to == null || !i.getDueDate().isAfter(to))
                .map(i -> toResponse(i, vehicles.get(vehicleIdOf.apply(i.getPlan())), byPlan.get(i.getPlan().getId()), today))
                .filter(i -> status == null || i.status() == status)
                .toList();
    }

    // ------------------------------------------------------------------ tahsilat

    @Override
    @Transactional
    public InstallmentResponse collect(UUID installmentId, CollectRequest r) {
        Installment i = getInstallment(installmentId);
        if (i.isPaid()) throw new InstallmentAlreadyPaidException();
        if (r.amountCents() != null && r.amountCents() != i.getAmountCents().longValue()) {
            // Kismi: taksit bolunur, bu kadari tahsil edilir, kalani ayni vadeyle acik kalir. Cek parca tahsil edilmez.
            if (r.amountCents() > i.getAmountCents() || i.getMethod() == PaymentMethod.CHEQUE) throw new InvalidPartialAmountException();
            i = lockInstallment(installmentId);
            if (i.isPaid()) throw new InstallmentAlreadyPaidException();
            split(i, r.amountCents());
        }
        PaymentPlan plan = i.getPlan();
        Vehicle vehicle = vehicleOf(plan);
        if (r.accountId() == null) throw new InvalidAccountException();
        LedgerEntry entry = settle(i, plan.getDirection(), vehicle, r.accountId(), r.date(),
                description(plan.getDirection(), i.getMethod()));
        OffsetDateTime paidAt = now();
        // Kosullu guncelleme: baska bir istek ayni anda tahsil ettiyse 0 satir; hata transaction'i (tahsilat hareketiyle) geri alir
        if (installmentRepository.markPaid(i.getId(), entry.getId(), paidAt) == 0) throw new InstallmentAlreadyPaidException();
        i.setPaidEntryId(entry.getId());   // bellekteki nesne de guncel (ayni degerler; ek UPDATE zararsiz)
        i.setPaidAt(paidAt);
        syncHolder(i, r.date());
        // Ortak aracin alis taksiti: odeme sahiplere paylarina gore bolunur (cepten kisim sermaye girisi)
        if (plan.getDirection() == PlanDirection.PURCHASE) coOwnershipService.syncContributions(vehicle);
        afterSaleCollection(plan);   // ortakli / konsinye satista tahsilat sahiplere dagitilir
        return single(i, vehicle);
    }

    @Override
    @Transactional
    public InstallmentResponse undo(UUID installmentId) {
        Installment i = getInstallment(installmentId);
        if (!i.isPaid()) throw new InstallmentNotPaidException();
        // Ciro iki taksiti birlikte kapatir: hangisinden geri alinirsa alinsin ciro butunuyle geri alinir
        if (i.isEndorsed()) {
            undoEndorsement(i);
            return single(i, null);
        }
        Optional<Installment> endorsedSource = installmentRepository.findByEndorseEntryId(i.getPaidEntryId());
        if (endorsedSource.isPresent()) {
            undoEndorsement(endorsedSource.get());
            return single(i, null);
        }
        ledgerService.reverse(i.getPaidEntryId());
        i.setPaidEntryId(null);
        i.setPaidAt(null);
        syncHolder(i, null);
        if (i.getPlan().getDirection() == PlanDirection.PURCHASE) {
            installmentRepository.flush();   // katki hesabi odenmis taksitleri sorgular
            coOwnershipService.syncContributions(vehicleOf(i.getPlan()));
        }
        afterSaleCollection(i.getPlan());
        return single(i, null);
    }

    @Override
    @Transactional
    public InstallmentResponse update(UUID installmentId, InstallmentUpdateRequest r) {
        Installment i = getInstallment(installmentId);
        if (i.isPaid()) {
            if (i.getMethod() != PaymentMethod.OPEN_ACCOUNT) throw new InstallmentAlreadyPaidException();
            i.setDescription(blankToNull(r.description()));
            return single(i, null);
        }
        i.setDueDate(r.dueDate());
        i.setNoteNo(blankToNull(r.noteNo()));
        if (r.bankName() != null) i.setBankName(blankToNull(r.bankName()));
        if (r.drawerName() != null) i.setDrawerName(blankToNull(r.drawerName()));
        if (i.getMethod() == PaymentMethod.OPEN_ACCOUNT) i.setDescription(blankToNull(r.description()));
        return single(i, null);
    }

    // ------------------------------------------------------------------ cek / senet: ciro ve karsiliksiz

    @Override
    @Transactional
    public InstallmentResponse endorse(UUID installmentId, EndorseRequest r) {
        Installment i = lockInstallment(installmentId);
        if (i.isPaid() || i.getPlan().getDirection() != PlanDirection.SALE || !i.getMethod().isInstrument()) {
            throw new EndorseNotAllowedException();
        }
        Contact to = getContact(r.toContactId(), i.getTenantId());
        if (to.getId().equals(i.getPayer().getId())) throw new InvalidEndorseTargetException();

        Installment target = null;
        Vehicle toVehicle = null;
        if (r.targetInstallmentId() != null) {
            target = lockInstallment(r.targetInstallmentId());
            if (target.isPaid() || target.getPlan().getDirection() != PlanDirection.PURCHASE
                    || !target.getPayer().getId().equals(to.getId()) || target.getAmountCents() < i.getAmountCents()) {
                throw new InvalidEndorseTargetException();
            }
            // Alis taksiti cekten buyukse: cek kadari ciroyla, kalani ayri taksit olarak acik kalir (or. nakitle odenir)
            if (target.getAmountCents() > i.getAmountCents()) split(target, i.getAmountCents());
            toVehicle = vehicleOf(target.getPlan());
        }

        Vehicle fromVehicle = vehicleOf(i.getPlan());
        String what = i.getMethod() == PaymentMethod.CHEQUE ? "Çek" : "Senet";
        LedgerService.Endorsement e = ledgerService.writeEndorsement(fromVehicle, i.getPayer(), toVehicle, to, i.getAmountCents(),
                r.date(), what + " ciro edildi: " + to.getName(), what + " cirosu (" + i.getPayer().getName() + ")");
        OffsetDateTime paidAt = now();
        if (installmentRepository.markPaid(i.getId(), e.in().getId(), paidAt) == 0) throw new InstallmentAlreadyPaidException();
        i.setPaidEntryId(e.in().getId());
        i.setPaidAt(paidAt);
        i.setEndorsedTo(to);
        i.setEndorseEntryId(e.out().getId());
        i.setBouncedAt(null);
        syncHolder(i, r.date());   // ciro: dukkan sermayedarin evrakini kullandi
        if (target != null) {
            if (installmentRepository.markPaid(target.getId(), e.out().getId(), paidAt) == 0) throw new InstallmentAlreadyPaidException();
            target.setPaidEntryId(e.out().getId());
            target.setPaidAt(paidAt);
            installmentRepository.flush();   // katki hesabi odenmis taksitleri sorgular
            coOwnershipService.syncContributions(toVehicle);
        }
        afterSaleCollection(i.getPlan());
        return single(i, fromVehicle);
    }

    @Override
    @Transactional
    public InstallmentResponse bounce(UUID installmentId) {
        Installment i = lockInstallment(installmentId);
        if (i.getPlan().getDirection() != PlanDirection.SALE || !i.getMethod().isInstrument()) throw new BounceNotAllowedException();
        if (i.isEndorsed()) {
            undoEndorsement(i);
        } else if (i.isPaid()) {
            ledgerService.reverse(i.getPaidEntryId());
            i.setPaidEntryId(null);
            i.setPaidAt(null);
            syncHolder(i, null);
        }
        i.setBouncedAt(now());
        afterSaleCollection(i.getPlan());   // karsiliksiz: dagitilan pay geri alinir, risk payi oraninda sahiplerde
        return single(i, null);
    }

    /** Ciroyu geri alir: iki hareket terslenir, ceki veren yeniden borclu, cekle odenen taksit (varsa) yeniden acik olur. */
    private void undoEndorsement(Installment source) {
        UUID out = source.getEndorseEntryId();
        Optional<Installment> target = installmentRepository.findByPaidEntryId(out);
        ledgerService.reverse(source.getPaidEntryId());
        ledgerService.reverse(out);
        source.setPaidEntryId(null);
        source.setPaidAt(null);
        source.setEndorsedTo(null);
        source.setEndorseEntryId(null);
        syncHolder(source, null);
        target.ifPresent(t -> {
            t.setPaidEntryId(null);
            t.setPaidAt(null);
            installmentRepository.flush();   // katki hesabi odenmis taksitleri sorgular
            coOwnershipService.syncContributions(vehicleOf(t.getPlan()));
        });
        afterSaleCollection(source.getPlan());
    }

    /** Satis planinda tahsilat degisti: planin ortakli / konsinye araclarinda tahsilat dagitimi yeniden yazilir. */
    private void afterSaleCollection(PaymentPlan plan) {
        if (plan.getDirection() != PlanDirection.SALE) return;
        installmentRepository.flush();
        List<UUID> ids = new ArrayList<>();
        if (plan.getVehicleId() != null) ids.add(plan.getVehicleId());
        else basketRepository.findByPlanIdOrderByCreatedAtAscIdAsc(plan.getId()).forEach(l -> ids.add(l.getVehicleId()));
        for (Vehicle v : vehicleRepository.findAllById(ids)) {
            if (v.getStatus() != VehicleStatus.IN_STOCK
                    && (v.getOwnership() == Ownership.CO_OWNED || v.getOwnership() == Ownership.CONSIGNMENT)) {
                coOwnershipService.resyncProfitShares(v);
            }
        }
    }

    /**
     * Evrak sermayedarinsa: odenen taksit onun sermayesine yazilir, odemesi geri alinan taksitin kredisi terslenir.
     * Her durum degisikliginden sonra cagrilir; zaten uygunsa bir sey yapmaz.
     */
    private void syncHolder(Installment i, LocalDate date) {
        UUID holder = i.getPlan().getHolderUserId();
        if (holder == null) return;
        if (i.isPaid() && i.getHolderEntryId() == null) {
            String what = i.getMethod() == PaymentMethod.CHEQUE ? "Çek" : "Senet";
            LedgerEntry credit = ledgerService.creditHolder(holder, i.getTenantId(), i.getAmountCents(), date != null ? date : today(),
                    what + " tahsili: " + i.getPayer().getName() + (i.getNoteNo() != null ? " · " + i.getNoteNo() : ""));
            if (credit != null) i.setHolderEntryId(credit.getId());
        } else if (!i.isPaid() && i.getHolderEntryId() != null) {
            ledgerService.reverse(i.getHolderEntryId());
            i.setHolderEntryId(null);
        }
    }

    /**
     * Taksiti boler: bu taksit firstCents olur, kalani ayni vade, yontem, odeyen ve evrak bilgisiyle yeni taksit. Plan toplami ve
     * defter degismez (satis / alis hareketi plan toplamiyla yazildi). Taksit kilitli ve odenmemis olmali.
     */
    private Installment split(Installment i, long firstCents) {
        long rest = i.getAmountCents() - firstCents;
        if (installmentRepository.resize(i.getId(), firstCents) == 0) throw new InstallmentAlreadyPaidException();
        i.setAmountCents(firstCents);
        Installment r = new Installment();
        r.setTenantId(i.getTenantId());
        r.setPlan(i.getPlan());
        r.setSeq((short) (installmentRepository.maxSeq(i.getPlan().getId()) + 1));
        r.setDueDate(i.getDueDate());
        r.setAmountCents(rest);
        r.setMethod(i.getMethod());
        r.setNoteNo(i.getNoteNo());
        r.setBankName(i.getBankName());
        r.setDrawerName(i.getDrawerName());
        r.setPayer(i.getPayer());
        return installmentRepository.save(r);
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

    private static void ensureNoOpenAccount(List<InstallmentRequest> rows) {
        if (rows.stream().anyMatch(row -> row.method() == PaymentMethod.OPEN_ACCOUNT)) {
            throw new InvalidOpenAccountUsageException();
        }
    }

    private LedgerEntry settle(Installment i, PlanDirection direction, Vehicle vehicle, UUID accountId, LocalDate date, String description) {
        return ledgerService.writeSettlement(direction, vehicle, i.getPayer(), accountId, i.getAmountCents(), date, description);
    }

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
        if (direction == PlanDirection.PURCHASE) {
            return switch (method) {
                case PROMISSORY_NOTE -> "Senet ödemesi";
                case OPEN_ACCOUNT -> "Açık hesap ödemesi";
                case CHEQUE -> "Çek ödemesi";
                default -> "Ödeme";
            };
        }
        return switch (method) {
            case PROMISSORY_NOTE -> "Senet tahsilatı";
            case OPEN_ACCOUNT -> "Açık hesap tahsilatı";
            case CHEQUE -> "Çek tahsilatı";
            case INSTITUTION -> "Kurum ödemesi";
            default -> "Tahsilat";
        };
    }

    private InstallmentResponse single(Installment i, Vehicle vehicle) {
        Vehicle v = vehicle != null ? vehicle : vehicleOf(i.getPlan());
        return toResponse(i, ref(v), installmentRepository.findByPlanIds(List.of(i.getPlan().getId())), today());
    }

    /** Pesinat: plan kurulurken ayni islemde tahsil / odenen satir (V15 isareti). Geri alinip sonra tahsil edilse de pesinattir. */
    static boolean isDownPayment(Installment i) {
        return i.isDownPayment();
    }

    /** siblings: ayni planin butun taksitleri. Taksit no ve sayisi pesinat haric sayilir ("Pesinat", "1. taksit" ...). */
    private static InstallmentResponse toResponse(Installment i, VehicleRef vehicle, List<Installment> siblings, LocalDate today) {
        int count = siblings.size();
        List<UUID> regular = siblings.stream().filter(s -> !isDownPayment(s))
                .sorted(Comparator.comparing(Installment::getSeq)).map(Installment::getId).toList();
        boolean down = isDownPayment(i);
        InstallmentStatus status = i.isPaid() ? InstallmentStatus.PAID
                : i.getDueDate().isBefore(today) ? InstallmentStatus.OVERDUE : InstallmentStatus.UPCOMING;
        long daysLate = status == InstallmentStatus.OVERDUE ? ChronoUnit.DAYS.between(i.getDueDate(), today) : 0;
        PaymentPlan p = i.getPlan();
        return new InstallmentResponse(i.getId(), p.getId(), p.getDirection(), vehicle, i.getSeq(), count, i.getDueDate(),
                i.getAmountCents(), i.getMethod(), i.getNoteNo(), summary(i.getPayer()), summary(p.getContact()),
                status, daysLate, i.getPaidEntryId(), i.getPaidAt(), i.getBankName(), i.getDrawerName(), summary(i.getEndorsedTo()),
                i.getBouncedAt() != null && !i.isPaid(), down, down ? 0 : regular.indexOf(i.getId()) + 1, regular.size(),
                userSummary(p.getHolder()), i.getDescription());
    }

    private Installment lockInstallment(UUID id) {
        Installment i = installmentRepository.lockById(id).orElseThrow(RecordNotFoundException::new);
        if (!i.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return i;
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
        return v == null ? null : new VehicleRef(v.getId(), v.getPlate(), v.getMake(), v.getModel());
    }

    /** Planin araci; aracsiz planda (gecmis senet / cek) null. */
    private Vehicle vehicleOf(PaymentPlan plan) {
        return plan.getVehicleId() == null ? null
                : vehicleRepository.findById(plan.getVehicleId()).orElseThrow(RecordNotFoundException::new);
    }

    private static ContactSummary summary(Contact c) {
        return c == null ? null : new ContactSummary(c.getId(), c.getName(), c.getPhone());
    }

    private static ContactSummary userSummary(PanelUser u) {
        return u == null ? null : new ContactSummary(u.getId(), u.getFullName(), u.getPhone());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
