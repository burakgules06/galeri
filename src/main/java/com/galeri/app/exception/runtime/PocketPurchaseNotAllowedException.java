package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Cepten alis: sadece dukkan sahibi, sadece galeri araci ve pesin (odeme plani yok), kisiden alista; cepten kisim alis bedelini asamaz.
public class PocketPurchaseNotAllowedException extends ApplicationRuntimeException {
    public PocketPurchaseNotAllowedException(String message) {
        super(message, ErrorMessageConstant.POCKET_PURCHASE_NOT_ALLOWED);
    }
}
