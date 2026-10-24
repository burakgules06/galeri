package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Plaka il kodu + 1-3 harf + 2-4 rakam bicimine uymuyor.
public class InvalidPlateException extends ApplicationRuntimeException {
    public InvalidPlateException() {
        super("Plaka geçersiz. Örnek: 34 ABC 123", ErrorMessageConstant.INVALID_PLATE);
    }
}
