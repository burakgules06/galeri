package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Istenen id veritabaninda yok.
public class KayitBulunamadiException extends ApplicationRuntimeException {
    public KayitBulunamadiException() {
        super("Kayıt bulunamadı", ErrorMessageConstant.KAYIT_BULUNAMADI);
    }
}
