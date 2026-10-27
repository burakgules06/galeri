package com.galeri.app.model.enums;

// Degerler V12 migration'indaki CHECK kisitiyla birebir ayni olmali.
// INSTITUTION: Evim sistemi ya da banka kredisi; odeyen her zaman kurumdur (ORGANIZATION).
// CHEQUE (V12): vadeli cek; bankasi ve keside edeni taksitte tutulur. Cek ve senet ciro edilebilir.
public enum PaymentMethod {
    CASH, TRANSFER, PROMISSORY_NOTE, INSTITUTION, CHEQUE;

    /** Elde tutulan kiymetli evrak: ciro edilebilir, karsiliksiz cikabilir. */
    public boolean isInstrument() {
        return this == PROMISSORY_NOTE || this == CHEQUE;
    }
}
