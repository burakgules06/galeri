package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Satilmis arac ikinci kez satilamaz.
public class AracZatenSatildiException extends ApplicationRuntimeException {
    public AracZatenSatildiException() {
        super("Bu araç zaten satılmış", ErrorMessageConstant.ARAC_ZATEN_SATILDI);
    }
}
