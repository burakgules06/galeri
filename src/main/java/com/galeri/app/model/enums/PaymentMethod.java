package com.galeri.app.model.enums;

// Degerler installment_method_check kisitiyla birebir ayni olmali.
// INSTITUTION: Evim sistemi ya da banka kredisi; odeyen her zaman kurumdur (ORGANIZATION).
// CHEQUE (V12): vadeli cek; bankasi ve keside edeni taksitte tutulur. Cek ve senet ciro edilebilir.
// OPEN_ACCOUNT (V20): senetsiz sozlu borc; satis ve alis planinda da kullanilir (V23). CREDIT_CARD (V23): kredi karti, POS.
public enum PaymentMethod {
    CASH, TRANSFER, PROMISSORY_NOTE, INSTITUTION, CHEQUE, OPEN_ACCOUNT, CREDIT_CARD;

    /** Elde tutulan kiymetli evrak: ciro edilebilir, karsiliksiz cikabilir. */
    public boolean isInstrument() {
        return this == PROMISSORY_NOTE || this == CHEQUE;
    }
}
