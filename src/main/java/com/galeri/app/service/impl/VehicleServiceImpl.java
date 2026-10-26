package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CatalogUnknownException;
import com.galeri.app.exception.runtime.ConsignmentDetailsRequiredException;
import com.galeri.app.exception.runtime.InvalidDateException;
import com.galeri.app.exception.runtime.InvalidShopCutException;
import com.galeri.app.exception.runtime.PlateAlreadyInStockException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.exception.runtime.VehicleAlreadySoldException;
import com.galeri.app.exception.runtime.VehicleNotSoldException;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.entity.VehiclePart;
import com.galeri.app.model.enums.BodyPart;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PartCondition;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.VehiclePartsRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.ContactSummary;
import com.galeri.app.model.response.DamageSummary;
import com.galeri.app.model.response.StaffSummary;
import com.galeri.app.model.response.VehicleDetail;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.model.response.VehicleSummary;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehiclePartRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CatalogService;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.util.PlateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    // Tek galeri Turkiye'de; tenant.timezone ileride kullanilabilir.
    static final ZoneId DEALER_ZONE = ZoneId.of("Europe/Istanbul");

    private final VehicleRepository vehicleRepository;
    private final VehiclePartRepository vehiclePartRepository;
    private final ContactRepository contactRepository;
    private final PanelUserRepository panelUserRepository;
    private final LedgerService ledgerService;
    private final PaymentPlanService paymentPlanService;
    private final CatalogService catalogService;
    private final CoOwnershipService coOwnershipService;
    private final Clock clock;

    // ------------------------------------------------------------------ okuma

    @Override
    @Transactional(readOnly = true)
    public List<VehicleSummary> search(String q, VehicleStatus status) {
        String query = q == null ? "" : q.trim();
        boolean admin = CurrentUser.isAdmin();
        List<Vehicle> vehicles = vehicleRepository.search(CurrentUser.tenantId(), status, query, admin,
                admin ? phoneQuery(query) : "");
        if (vehicles.isEmpty()) return List.of();

        Map<UUID, List<VehiclePart>> parts = vehiclePartRepository
                .findByVehicleIdIn(vehicles.stream().map(Vehicle::getId).toList()).stream()
                .collect(Collectors.groupingBy(VehiclePart::getVehicleId));

        LocalDate today = today();
        return vehicles.stream()
                .map(v -> toSummary(v, parts.getOrDefault(v.getId(), List.of()), today, admin))
                .toList();
    }

    /**
     * Telefonla arama icin rakamlar: "0530 111 22 33" -> "5301112233" (kayitli bicim 90XXXXXXXXXX'in parcasi).
     * 3 rakamdan kisaysa telefonla aranmaz (plaka ve model yilindaki rakamlarla karismasin).
     */
    static String phoneQuery(String q) {
        String digits = q.replaceAll("\\D", "").replaceFirst("^0+", "");
        return digits.length() >= 3 ? digits : "";
    }

    @Override
    @Transactional(readOnly = true)
    public Object detail(UUID id) {
        Vehicle vehicle = getVehicle(id);
        VehicleDetail detail = toDetail(vehicle);
        return CurrentUser.isAdmin() ? toAdmin(vehicle, detail) : detail;
    }

    // ------------------------------------------------------------------ yazma

    @Override
    @Transactional
    public VehicleDetailAdmin create(VehicleRequest request) {
        UUID tenantId = CurrentUser.tenantId();
        String plate = PlateUtil.normalize(request.plate());
        if (vehicleRepository.existsByTenantIdAndPlateAndStatus(tenantId, plate, VehicleStatus.IN_STOCK)) {
            throw new PlateAlreadyInStockException();
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setTenantId(tenantId);
        vehicle.setPlate(plate);
        apply(vehicle, request);
        Vehicle saved = vehicleRepository.save(vehicle);
        coOwnershipService.syncOwners(saved, request.shares());
        syncPurchase(saved, request);
        coOwnershipService.syncContributions(saved);   // ortak aracta cepten katki ve nakit sermaye kontrolu
        return toAdmin(saved, toDetail(saved));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin update(UUID id, VehicleRequest request) {
        Vehicle vehicle = getVehicle(id);
        String plate = PlateUtil.normalize(request.plate());
        boolean plateChanged = !plate.equals(vehicle.getPlate());
        if (plateChanged && vehicle.getStatus() == VehicleStatus.IN_STOCK
                && vehicleRepository.existsByTenantIdAndPlateAndStatus(vehicle.getTenantId(), plate, VehicleStatus.IN_STOCK)) {
            throw new PlateAlreadyInStockException();
        }
        vehicle.setPlate(plate);
        apply(vehicle, request);
        if (vehicle.getSaleDate() != null && vehicle.getSaleDate().isBefore(vehicle.getPurchaseDate())) {
            throw new InvalidDateException();
        }
        coOwnershipService.syncOwners(vehicle, request.shares());
        syncPurchase(vehicle, request);
        // Ortak aracta katkilar; satilmissa kar paylari da yeniden yazilir
        coOwnershipService.syncContributions(vehicle);
        // Satilmis aracta sahiplik ya da komisyon degistiyse sahibine borc yeniden yazilir
        if (vehicle.getStatus() != VehicleStatus.IN_STOCK) ledgerService.syncConsignmentSettlement(vehicle);
        return toAdmin(vehicle, toDetail(vehicle));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin updateParts(UUID id, VehiclePartsRequest request) {
        Vehicle vehicle = getVehicle(id);
        Map<BodyPart, VehiclePart> existing = vehiclePartRepository.findByVehicleId(vehicle.getId()).stream()
                .collect(Collectors.toMap(VehiclePart::getPart, Function.identity()));

        // Yerinde guncelleme: sil + ekle yapilmaz (Hibernate insert'leri delete'lerden once
        // calistirdigi icin (vehicle_id, part) tekillik kisiti patlar).
        request.parts().forEach((part, condition) -> {
            VehiclePart record = existing.get(part);
            if (condition == PartCondition.ORIGINAL) {
                if (record != null) vehiclePartRepository.delete(record);
            } else if (record != null) {
                record.setCondition(condition);
            } else {
                VehiclePart created = new VehiclePart();
                created.setTenantId(vehicle.getTenantId());
                created.setVehicleId(vehicle.getId());
                created.setPart(part);
                created.setCondition(condition);
                vehiclePartRepository.save(created);
            }
        });
        vehiclePartRepository.flush();
        return toAdmin(vehicle, toDetail(vehicle));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin sell(UUID id, VehicleSaleRequest request) {
        Vehicle vehicle = getVehicle(id);
        if (vehicle.getStatus() != VehicleStatus.IN_STOCK) throw new VehicleAlreadySoldException();
        if (request.saleDate().isBefore(vehicle.getPurchaseDate())) throw new InvalidDateException();

        Contact buyer = getContact(request.buyerId(), vehicle.getTenantId());
        vehicle.setStatus(VehicleStatus.SOLD);
        vehicle.setSaleDate(request.saleDate());
        vehicle.setBuyer(buyer);
        vehicle.setSoldBy(getStaff(request.soldById(), vehicle.getTenantId()));
        vehicle.setSalePriceCents(request.salePriceCents());
        paymentPlanService.recordSale(vehicle, buyer, request);
        ledgerService.syncConsignmentSettlement(vehicle);   // konsinyede: BALANCE(sahibi) -> INCOME (satis - komisyon)
        coOwnershipService.onSale(vehicle, request.ownerReturns());   // ortak aracta kar paylari ve ortaga donus
        return toAdmin(vehicle, toDetail(vehicle));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin undoSale(UUID id) {
        Vehicle vehicle = getVehicle(id);
        if (vehicle.getStatus() != VehicleStatus.SOLD) throw new VehicleNotSoldException();
        // Ayni plakayla stokta baska arac varsa geri donus tekillik kisitini bozar
        if (vehicleRepository.existsByTenantIdAndPlateAndStatus(vehicle.getTenantId(), vehicle.getPlate(), VehicleStatus.IN_STOCK)) {
            throw new PlateAlreadyInStockException();
        }
        coOwnershipService.onUndoSale(vehicle);   // ortaga nakit donus yapildiysa 1043; kar paylari terslenir
        paymentPlanService.undoSale(vehicle);
        vehicle.setStatus(VehicleStatus.IN_STOCK);
        vehicle.setSaleDate(null);
        vehicle.setBuyer(null);
        vehicle.setSoldBy(null);
        vehicle.setSalePriceCents(null);
        ledgerService.syncConsignmentSettlement(vehicle);   // stoktaki araca sahibine borc kalmaz
        coOwnershipService.syncContributions(vehicle);      // katkilar yeniden araca baglanir: nakit sermaye yetmeli
        return toAdmin(vehicle, toDetail(vehicle));
    }

    // ------------------------------------------------------------------ yardimcilar

    private void apply(Vehicle vehicle, VehicleRequest r) {
        applyMakeModel(vehicle, r);
        vehicle.setModelYear(r.modelYear());
        vehicle.setFuel(r.fuel());
        vehicle.setTransmission(r.transmission());
        vehicle.setMileageKm(r.mileageKm());
        vehicle.setColor(blankToNull(r.color()));
        vehicle.setTramerCents(r.tramerCents());
        vehicle.setNotes(blankToNull(r.notes()));
        vehicle.setPurchaseDate(r.purchaseDate());
        vehicle.setBoughtBy(getStaff(r.boughtById(), vehicle.getTenantId()));

        Ownership ownership = r.ownershipOrDefault();
        vehicle.setOwnership(ownership);
        if (ownership == Ownership.CONSIGNMENT) {
            CommissionType type = r.commissionType();
            if (r.consignorId() == null || type == null
                    || (type == CommissionType.RATE && r.commissionRateBp() == null)
                    || (type == CommissionType.FIXED && r.commissionCents() == null)) {
                throw new ConsignmentDetailsRequiredException();
            }
            // Arac galerinin mali degil: alis bedeli ve saticisi yok, sahibi var
            vehicle.setConsignor(getContact(r.consignorId(), vehicle.getTenantId()));
            vehicle.setCommissionType(type);
            vehicle.setCommissionRateBp(type == CommissionType.RATE ? r.commissionRateBp() : null);
            vehicle.setCommissionCents(type == CommissionType.FIXED ? r.commissionCents() : null);
            vehicle.setSeller(null);
            vehicle.setActualPurchaseCents(null);
            vehicle.setOfficialPurchaseCents(null);
        } else {
            vehicle.setConsignor(null);
            vehicle.setCommissionType(null);
            vehicle.setCommissionRateBp(null);
            vehicle.setCommissionCents(null);
            vehicle.setSeller(getContact(r.sellerId(), vehicle.getTenantId()));
            vehicle.setActualPurchaseCents(r.actualPurchaseCents());
            vehicle.setOfficialPurchaseCents(r.officialPurchaseCents());
        }
        applyShopCut(vehicle, r);
    }

    /** Dukkan payi sadece ortak aracta, istege bagli: oranda baz puan, sabitte tutar. */
    private static void applyShopCut(Vehicle vehicle, VehicleRequest r) {
        CommissionType type = r.shopCutType();
        if (type != null && (vehicle.getOwnership() != Ownership.CO_OWNED
                || (type == CommissionType.RATE && r.shopCutRateBp() == null)
                || (type == CommissionType.FIXED && r.shopCutCents() == null))) {
            throw new InvalidShopCutException();
        }
        vehicle.setShopCutType(type);
        vehicle.setShopCutRateBp(type == CommissionType.RATE ? r.shopCutRateBp() : null);
        vehicle.setShopCutCents(type == CommissionType.FIXED ? r.shopCutCents() : null);
    }

    /**
     * Marka / model katalogdan: yeni kayitta ya da marka / model degistiyse katalogda olmali (katalog yazimina cevrilir,
     * paket korunur). Katalog oncesi eski kayit marka / modeline dokunulmadikca aynen kalir. "Listede yok" onaylandiysa
     * once galerinin katalogune eklenir.
     */
    private void applyMakeModel(Vehicle vehicle, VehicleRequest r) {
        String make = r.make().trim();
        String model = r.model().trim().replaceAll("\\s+", " ");
        boolean unchanged = vehicle.getId() != null && make.equalsIgnoreCase(vehicle.getMake()) && model.equals(vehicle.getModel());
        if (unchanged) return;
        if (Boolean.TRUE.equals(r.addToCatalog())) {
            String base = r.catalogModel() == null || r.catalogModel().isBlank() ? model : r.catalogModel();
            catalogService.add(vehicle.getTenantId(), make, base);
        }
        CatalogService.Resolved resolved = catalogService.resolve(vehicle.getTenantId(), make, model)
                .orElseThrow(CatalogUnknownException::new);
        vehicle.setMake(resolved.make());
        vehicle.setModel(resolved.model());
    }

    /** Konsinye aracin alisi deftere yazilmaz: varsa onceki alis hareketi / plani kaldirilir. */
    private void syncPurchase(Vehicle vehicle, VehicleRequest r) {
        if (vehicle.getOwnership() == Ownership.CONSIGNMENT) {
            paymentPlanService.syncPurchase(vehicle, null, null);
        } else {
            paymentPlanService.syncPurchase(vehicle, r.purchaseAccountId(), r.purchasePlan());
        }
    }

    private Vehicle getVehicle(UUID id) {
        Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!vehicle.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return vehicle;
    }

    private Contact getContact(UUID id, UUID tenantId) {
        if (id == null) return null;
        Contact contact = contactRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!contact.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        return contact;
    }

    private PanelUser getStaff(UUID id, UUID tenantId) {
        if (id == null) return null;
        PanelUser staff = panelUserRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (staff.getTenant() == null || !staff.getTenant().getId().equals(tenantId)) {
            throw new SourceNotBelongToTenantException();
        }
        return staff;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(DEALER_ZONE));
    }

    /** Stokta ise bugune kadar, satildiysa satis gunune kadar gecen gun. */
    static long daysInStock(Vehicle vehicle, LocalDate today) {
        LocalDate end = vehicle.getSaleDate() != null ? vehicle.getSaleDate() : today;
        return Math.max(0, ChronoUnit.DAYS.between(vehicle.getPurchaseDate(), end));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private VehicleSummary toSummary(Vehicle v, List<VehiclePart> parts, LocalDate today, boolean admin) {
        int locallyPainted = 0, painted = 0, replaced = 0;
        for (VehiclePart p : parts) {
            switch (p.getCondition()) {
                case LOCALLY_PAINTED -> locallyPainted++;
                case PAINTED -> painted++;
                case REPLACED -> replaced++;
                case ORIGINAL -> { }
            }
        }
        return new VehicleSummary(v.getId(), v.getPlate(), v.getMake(), v.getModel(), v.getModelYear(), v.getMileageKm(),
                v.getFuel(), v.getTransmission(), v.getColor(), v.getStatus(), v.getPurchaseDate(), v.getSaleDate(),
                daysInStock(v, today), new DamageSummary(locallyPainted, painted, replaced),
                admin ? contactSummary(v.getBuyer()) : null, v.getOwnership());
    }

    private VehicleDetail toDetail(Vehicle v) {
        Map<BodyPart, PartCondition> parts = new EnumMap<>(BodyPart.class);
        for (BodyPart p : BodyPart.values()) parts.put(p, PartCondition.ORIGINAL);
        if (v.getId() != null) {
            vehiclePartRepository.findByVehicleId(v.getId()).forEach(p -> parts.put(p.getPart(), p.getCondition()));
        }
        return new VehicleDetail(v.getId(), v.getPlate(), v.getMake(), v.getModel(), v.getModelYear(), v.getMileageKm(),
                v.getFuel(), v.getTransmission(), v.getColor(), v.getTramerCents(), v.getNotes(), v.getStatus(),
                v.getPurchaseDate(), v.getSaleDate(), daysInStock(v, today()), parts, v.getOwnership());
    }

    private VehicleDetailAdmin toAdmin(Vehicle v, VehicleDetail detail) {
        return new VehicleDetailAdmin(detail,
                contactSummary(v.getSeller()), staffSummary(v.getBoughtBy()), v.getActualPurchaseCents(), v.getOfficialPurchaseCents(),
                contactSummary(v.getBuyer()), staffSummary(v.getSoldBy()), v.getSalePriceCents(),
                ledgerService.purchaseAccountId(v.getId()),
                contactSummary(v.getConsignor()), v.getCommissionType(), v.getCommissionRateBp(), v.getCommissionCents(),
                coOwnershipService.owners(v), v.getShopCutType(), v.getShopCutRateBp(), v.getShopCutCents());
    }

    private static ContactSummary contactSummary(Contact c) {
        return c == null ? null : new ContactSummary(c.getId(), c.getName(), c.getPhone());
    }

    private static StaffSummary staffSummary(PanelUser u) {
        return u == null ? null : new StaffSummary(u.getId(), u.getFullName());
    }
}
