package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Masraf sadece kasa ya da banka hesabindan odenir.
public class InvalidAccountException extends ApplicationRuntimeException {
    public InvalidAccountException() {
        super("Bu hesaptan ödeme yapılamaz, kasa ya da banka seçin", ErrorMessageConstant.INVALID_ACCOUNT);
    }
}
