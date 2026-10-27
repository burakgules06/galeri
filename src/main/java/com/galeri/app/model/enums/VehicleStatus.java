package com.galeri.app.model.enums;

// Degerler V2 migration'indaki CHECK kisitlariyla birebir ayni olmali.
public enum VehicleStatus {
    IN_STOCK, SOLD, CLOSED,
    /** Yanlis giris: hareketleri terslendi, listelerde gorunmez (V17). */
    REMOVED
}
