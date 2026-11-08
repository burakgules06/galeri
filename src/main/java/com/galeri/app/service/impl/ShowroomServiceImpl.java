package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.PhotoLimitException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.entity.VehiclePart;
import com.galeri.app.model.entity.VehiclePhoto;
import com.galeri.app.model.enums.PartCondition;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.ShowroomRequests;
import com.galeri.app.model.response.ShowroomResponses;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.repository.VehiclePartRepository;
import com.galeri.app.repository.VehiclePhotoRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.PhotoStorage;
import com.galeri.app.service.ShowroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowroomServiceImpl implements ShowroomService {

    static final int MAX_PHOTOS = 20;

    private final VehicleRepository vehicleRepository;
    private final VehiclePhotoRepository photoRepository;
    private final VehiclePartRepository partRepository;
    private final TenantRepository tenantRepository;
    private final PhotoStorage storage;

    @Override
    @Transactional(readOnly = true)
    public ShowroomResponses.Listing listing(UUID vehicleId) {
        return listingOf(ownVehicle(vehicleId));
    }

    @Override
    @Transactional
    public ShowroomResponses.Listing updateListing(UUID vehicleId, ShowroomRequests.Listing request) {
        Vehicle v = ownVehicle(vehicleId);
        v.setShowInShowroom(request.showInShowroom());
        v.setListingPriceCents(request.listingPriceCents());
        String text = request.listingDescription() == null ? null : request.listingDescription().trim();
        v.setListingDescription(text == null || text.isEmpty() ? null : text);
        log.info("Vitrin guncellendi: arac {} gorunur={}", v.getId(), v.isShowInShowroom());
        return listingOf(v);
    }

    @Override
    @Transactional
    public ShowroomResponses.Listing addPhoto(UUID vehicleId, byte[] content) {
        Vehicle v = ownVehicle(vehicleId);
        List<VehiclePhoto> existing = photoRepository.findByVehicleIdOrderByPositionAsc(v.getId());
        if (existing.size() >= MAX_PHOTOS) throw new PhotoLimitException();
        UUID id = UUID.randomUUID();
        PhotoStorage.Stored stored = storage.save(v.getTenantId(), id, content);
        afterRollback(() -> storage.delete(v.getTenantId(), id));
        VehiclePhoto photo = new VehiclePhoto();
        photo.setId(id);
        photo.setTenantId(v.getTenantId());
        photo.setVehicleId(v.getId());
        photo.setPosition(existing.isEmpty() ? 0 : existing.getLast().getPosition() + 1);
        photo.setWidth(stored.width());
        photo.setHeight(stored.height());
        photo.setSizeBytes(stored.sizeBytes());
        photoRepository.save(photo);
        return listingOf(v);
    }

    @Override
    @Transactional
    public ShowroomResponses.Listing deletePhoto(UUID vehicleId, UUID photoId) {
        Vehicle v = ownVehicle(vehicleId);
        VehiclePhoto photo = photoRepository.findById(photoId)
                .filter(p -> p.getVehicleId().equals(v.getId()))
                .orElseThrow(SourceNotBelongToTenantException::new);
        photoRepository.delete(photo);
        // Dosya, kayit kesin silinince (commit) silinir
        afterCommit(() -> storage.delete(v.getTenantId(), photoId));
        return listingOf(v);
    }

    @Override
    @Transactional
    public ShowroomResponses.Listing reorder(UUID vehicleId, ShowroomRequests.Reorder request) {
        Vehicle v = ownVehicle(vehicleId);
        List<VehiclePhoto> photos = photoRepository.findByVehicleIdOrderByPositionAsc(v.getId());
        Map<UUID, VehiclePhoto> byId = photos.stream().collect(Collectors.toMap(VehiclePhoto::getId, p -> p));
        if (request.photoIds().size() != photos.size() || !byId.keySet().equals(new HashSet<>(request.photoIds()))) {
            throw new SourceNotBelongToTenantException();
        }
        for (int i = 0; i < request.photoIds().size(); i++) byId.get(request.photoIds().get(i)).setPosition(i);
        return listingOf(v);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowroomResponses.Settings settings() {
        Tenant t = tenantRepository.findById(CurrentUser.tenantId()).orElseThrow(RecordNotFoundException::new);
        return new ShowroomResponses.Settings(t.getSlug(), t.getShowroomPhone());
    }

    @Override
    @Transactional
    public ShowroomResponses.Settings updateSettings(ShowroomRequests.Settings request) {
        Tenant t = tenantRepository.findById(CurrentUser.tenantId()).orElseThrow(RecordNotFoundException::new);
        String digits = request.phone() == null ? "" : request.phone().replaceAll("\\D", "");
        t.setShowroomPhone(digits.isEmpty() ? null : digits);
        return new ShowroomResponses.Settings(t.getSlug(), t.getShowroomPhone());
    }

    @Override
    @Transactional(readOnly = true)
    public ShowroomResponses.PublicGallery publicGallery(String slug) {
        Tenant t = activeTenant(slug);
        List<Vehicle> vehicles = vehicleRepository.findByTenantIdAndStatus(t.getId(), VehicleStatus.IN_STOCK).stream()
                .filter(Vehicle::isShowInShowroom)
                .sorted(Comparator.comparing(Vehicle::getPurchaseDate).reversed())
                .toList();
        List<UUID> ids = vehicles.stream().map(Vehicle::getId).toList();
        Map<UUID, List<VehiclePhoto>> photos = ids.isEmpty() ? Map.of() : photoRepository.findByVehicleIdInOrderByPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(VehiclePhoto::getVehicleId));
        Map<UUID, List<VehiclePart>> parts = ids.isEmpty() ? Map.of() : partRepository.findByVehicleIdIn(ids).stream()
                .collect(Collectors.groupingBy(VehiclePart::getVehicleId));
        List<ShowroomResponses.PublicCard> cards = vehicles.stream().map(v -> {
            List<VehiclePhoto> ps = photos.getOrDefault(v.getId(), List.of());
            List<VehiclePart> vp = parts.getOrDefault(v.getId(), List.of());
            return new ShowroomResponses.PublicCard(v.getId(), v.getMake(), v.getModel(), v.getModelYear(), v.getMileageKm(),
                    v.getFuel(), v.getTransmission(), v.getColor(), v.getListingPriceCents(),
                    ps.isEmpty() ? null : photo(ps.getFirst()), ps.size(),
                    count(vp, PartCondition.PAINTED) + count(vp, PartCondition.LOCALLY_PAINTED), count(vp, PartCondition.REPLACED));
        }).toList();
        return new ShowroomResponses.PublicGallery(t.getDisplayName(), t.getShowroomPhone(), cards);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowroomResponses.PublicVehicle publicVehicle(String slug, UUID vehicleId) {
        Tenant t = activeTenant(slug);
        Vehicle v = vehicleRepository.findById(vehicleId)
                .filter(x -> x.getTenantId().equals(t.getId()) && x.getStatus() == VehicleStatus.IN_STOCK && x.isShowInShowroom())
                .orElseThrow(RecordNotFoundException::new);
        List<ShowroomResponses.PublicPart> parts = partRepository.findByVehicleId(v.getId()).stream()
                .filter(p -> p.getCondition() != PartCondition.ORIGINAL)
                .map(p -> new ShowroomResponses.PublicPart(p.getPart(), p.getCondition()))
                .toList();
        List<ShowroomResponses.Photo> photos = photoRepository.findByVehicleIdOrderByPositionAsc(v.getId()).stream()
                .map(ShowroomServiceImpl::photo).toList();
        return new ShowroomResponses.PublicVehicle(v.getId(), v.getPlate(), v.getMake(), v.getModel(), v.getModelYear(),
                v.getMileageKm(), v.getFuel(), v.getTransmission(), v.getColor(), v.getListingPriceCents(), v.getTramerCents(),
                v.getListingDescription(), parts, photos, t.getDisplayName(), t.getShowroomPhone());
    }

    @Override
    @Transactional(readOnly = true)
    public Path photoFile(UUID photoId, boolean thumb) {
        return photoRepository.findById(photoId).map(p -> storage.path(p.getTenantId(), p.getId(), thumb)).orElse(null);
    }

    private Tenant activeTenant(String slug) {
        return tenantRepository.findBySlug(slug).filter(Tenant::isActive).orElseThrow(RecordNotFoundException::new);
    }

    /** Yoneticinin kendi galerisindeki (kaldirilmamis) araci. */
    private Vehicle ownVehicle(UUID vehicleId) {
        Vehicle v = vehicleRepository.findById(vehicleId).orElseThrow(RecordNotFoundException::new);
        if (!v.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (v.getStatus() == VehicleStatus.REMOVED) throw new RecordNotFoundException();
        return v;
    }

    private ShowroomResponses.Listing listingOf(Vehicle v) {
        List<ShowroomResponses.Photo> photos = photoRepository.findByVehicleIdOrderByPositionAsc(v.getId()).stream()
                .map(ShowroomServiceImpl::photo).toList();
        return new ShowroomResponses.Listing(v.isShowInShowroom(), v.getListingPriceCents(), v.getListingDescription(), photos,
                v.getStatus() == VehicleStatus.IN_STOCK);
    }

    static ShowroomResponses.Photo photo(VehiclePhoto p) {
        String base = "/api/public/photos/" + p.getId();
        return new ShowroomResponses.Photo(p.getId(), base + ".jpg", base + "_t.jpg", p.getWidth(), p.getHeight());
    }

    private static int count(List<VehiclePart> parts, PartCondition condition) {
        return (int) parts.stream().filter(p -> p.getCondition() == condition).count();
    }

    private static void afterCommit(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { r.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { r.run(); }
        });
    }

    private static void afterRollback(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) r.run(); }
        });
    }
}
