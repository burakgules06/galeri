package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Odenmis taksiti olan alisin bedeli, saticisi ya da odeme sekli (plandan pesine) degistirilemez.
public class PurchaseHasPaymentsException extends ApplicationRuntimeException {
    public PurchaseHasPaymentsException() {
        super("Ödenmiş taksiti olan alışın bedeli, satıcısı ya da ödeme şekli değiştirilemez. Önce ödemeleri geri alın.",
                ErrorMessageConstant.PURCHASE_HAS_PAYMENTS);
    }
}
