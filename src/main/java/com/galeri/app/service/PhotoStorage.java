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
}
