package com.galeri.app.model.enums;

// Degerler installment_method_check kisitiyla birebir ayni olmali.
// INSTITUTION: Evim sistemi ya da banka kredisi; odeyen her zaman kurumdur (ORGANIZATION).
// CHEQUE (V12): vadeli cek; bankasi ve keside edeni taksitte tutulur. Cek ve senet ciro edilebilir.
public enum PaymentMethod {
    CASH, TRANSFER, PROMISSORY_NOTE, INSTITUTION, CHEQUE, OPEN_ACCOUNT;

    /** Elde tutulan kiymetli evrak: ciro edilebilir, karsiliksiz cikabilir. */
    public boolean isInstrument() {
        return this == PROMISSORY_NOTE || this == CHEQUE;
    }
}
