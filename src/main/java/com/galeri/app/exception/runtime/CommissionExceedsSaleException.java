package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Sabit komisyon satis bedelinden buyuk olamaz (sahibine eksi tutar yazilmaz).
public class CommissionExceedsSaleException extends ApplicationRuntimeException {
    public CommissionExceedsSaleException() {
        super("Komisyon satış bedelinden büyük olamaz", ErrorMessageConstant.COMMISSION_EXCEEDS_SALE);
    }
}
