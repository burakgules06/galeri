package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Satis tarihi alis tarihinden once.
public class GecersizTarihException extends ApplicationRuntimeException {
    public GecersizTarihException() {
        super("Satış tarihi alış tarihinden önce olamaz", ErrorMessageConstant.GECERSIZ_TARIH);
    }
}
