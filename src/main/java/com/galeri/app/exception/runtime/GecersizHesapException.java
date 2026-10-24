package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Masraf sadece kasa ya da banka hesabindan odenir.
public class GecersizHesapException extends ApplicationRuntimeException {
    public GecersizHesapException() {
        super("Bu hesaptan ödeme yapılamaz, kasa ya da banka seçin", ErrorMessageConstant.GECERSIZ_HESAP);
    }
}
