package com.galeri.app.service;

import com.galeri.app.model.request.ShowroomRequests;
import com.galeri.app.model.response.ShowroomResponses;

import java.nio.file.Path;
import java.util.UUID;

/** Musteri vitrini: aracin vitrin ayarlari ve fotograflari (yonetici) ve herkese acik sayfa. */
public interface ShowroomService {

    ShowroomResponses.Listing listing(UUID vehicleId);

    ShowroomResponses.Listing updateListing(UUID vehicleId, ShowroomRequests.Listing request);

    ShowroomResponses.Listing addPhoto(UUID vehicleId, byte[] content);

    ShowroomResponses.Listing deletePhoto(UUID vehicleId, UUID photoId);

    ShowroomResponses.Listing reorder(UUID vehicleId, ShowroomRequests.Reorder request);

    ShowroomResponses.Settings settings();

    ShowroomResponses.Settings updateSettings(ShowroomRequests.Settings request);

    /** Herkese acik: galerinin vitrindeki stok araclari. Galeri yoksa / pasifse RecordNotFoundException. */
    ShowroomResponses.PublicGallery publicGallery(String slug);

    ShowroomResponses.PublicVehicle publicVehicle(String slug, UUID vehicleId);

    /** Herkese acik fotograf dosyasi (id tahmin edilemez). Yoksa null. */
    Path photoFile(UUID photoId, boolean thumb);
}
