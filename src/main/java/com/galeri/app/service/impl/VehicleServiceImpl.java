package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InvalidDateException;
import com.galeri.app.exception.runtime.PlateAlreadyInStockException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.exception.runtime.VehicleAlreadySoldException;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.entity.VehiclePart;
import com.galeri.app.model.enums.BodyPart;
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
import com.galeri.app.service.LedgerService;
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
        ledgerService.syncVehiclePurchase(saved, request.purchaseAccountId());
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
        ledgerService.syncVehiclePurchase(vehicle, request.purchaseAccountId());
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

        vehicle.setStatus(VehicleStatus.SOLD);
        vehicle.setSaleDate(request.saleDate());
        vehicle.setBuyer(getContact(request.buyerId(), vehicle.getTenantId()));
        vehicle.setSoldBy(getStaff(request.soldById(), vehicle.getTenantId()));
        vehicle.setSalePriceCents(request.salePriceCents());
        return toAdmin(vehicle, toDetail(vehicle));
    }

    // ------------------------------------------------------------------ yardimcilar

    private void apply(Vehicle vehicle, VehicleRequest r) {
        vehicle.setMake(r.make().trim());
        vehicle.setModel(r.model().trim());
        vehicle.setModelYear(r.modelYear());
        vehicle.setFuel(r.fuel());
        vehicle.setTransmission(r.transmission());
        vehicle.setMileageKm(r.mileageKm());
        vehicle.setColor(blankToNull(r.color()));
        vehicle.setTramerCents(r.tramerCents());
        vehicle.setNotes(blankToNull(r.notes()));
        vehicle.setPurchaseDate(r.purchaseDate());
        vehicle.setSeller(getContact(r.sellerId(), vehicle.getTenantId()));
        vehicle.setBoughtBy(getStaff(r.boughtById(), vehicle.getTenantId()));
        vehicle.setActualPurchaseCents(r.actualPurchaseCents());
        vehicle.setOfficialPurchaseCents(r.officialPurchaseCents());
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
                admin ? contactSummary(v.getBuyer()) : null);
    }

    private VehicleDetail toDetail(Vehicle v) {
        Map<BodyPart, PartCondition> parts = new EnumMap<>(BodyPart.class);
        for (BodyPart p : BodyPart.values()) parts.put(p, PartCondition.ORIGINAL);
        if (v.getId() != null) {
            vehiclePartRepository.findByVehicleId(v.getId()).forEach(p -> parts.put(p.getPart(), p.getCondition()));
        }
        return new VehicleDetail(v.getId(), v.getPlate(), v.getMake(), v.getModel(), v.getModelYear(), v.getMileageKm(),
                v.getFuel(), v.getTransmission(), v.getColor(), v.getTramerCents(), v.getNotes(), v.getStatus(),
                v.getPurchaseDate(), v.getSaleDate(), daysInStock(v, today()), parts);
    }

    private VehicleDetailAdmin toAdmin(Vehicle v, VehicleDetail detail) {
        return new VehicleDetailAdmin(detail,
                contactSummary(v.getSeller()), staffSummary(v.getBoughtBy()), v.getActualPurchaseCents(), v.getOfficialPurchaseCents(),
                contactSummary(v.getBuyer()), staffSummary(v.getSoldBy()), v.getSalePriceCents(),
                ledgerService.purchaseAccountId(v.getId()));
    }

    private static ContactSummary contactSummary(Contact c) {
        return c == null ? null : new ContactSummary(c.getId(), c.getName(), c.getPhone());
    }

    private static StaffSummary staffSummary(PanelUser u) {
        return u == null ? null : new StaffSummary(u.getId(), u.getFullName());
    }
}
