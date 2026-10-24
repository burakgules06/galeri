package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Alis odeme planinin toplami (odenmis taksitler dahil) gercek alis bedeline kurusu kurusuna esit degil.
public class PurchaseTotalMismatchException extends ApplicationRuntimeException {
    public PurchaseTotalMismatchException() {
        super("Ödeme planının toplamı gerçek alış bedeline eşit olmalı", ErrorMessageConstant.PURCHASE_TOTAL_MISMATCH);
    }
}
