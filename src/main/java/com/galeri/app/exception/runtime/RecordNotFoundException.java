package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Istenen id veritabaninda yok.
public class RecordNotFoundException extends ApplicationRuntimeException {
    public RecordNotFoundException() {
        super("Kayıt bulunamadı", ErrorMessageConstant.RECORD_NOT_FOUND);
    }
}
