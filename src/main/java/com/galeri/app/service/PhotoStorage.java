package com.galeri.app.service;

import java.nio.file.Path;
import java.util.UUID;

/** Arac fotograflarinin diskte saklanmasi: buyuk (en fazla 1600 px) ve kucuk (480 px) JPEG. */
public interface PhotoStorage {

    record Stored(int width, int height, int sizeBytes) {}

    /** Resmi okur, kuculturup JPEG olarak yazar (EXIF / konum bilgisi atilir). Resim degilse InvalidPhotoException. */
    Stored save(UUID tenantId, UUID photoId, byte[] content);

    void delete(UUID tenantId, UUID photoId);

    /** thumb: liste icin kucuk boy. Dosya yoksa null. */
    Path path(UUID tenantId, UUID photoId, boolean thumb);

    /**
     * Galeri gorseli (logo ya da alt banner): saydamlik korunur, uzun kenar en fazla 1200 px, PNG. Resim degilse
     * InvalidPhotoException.
     */
    Stored saveLogo(UUID tenantId, UUID logoId, byte[] content);

    void deleteLogo(UUID tenantId, UUID logoId);

    /** Dosya yoksa null. */
    Path logoPath(UUID tenantId, UUID logoId);
}
