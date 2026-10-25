package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Masraf duzenle / sil sadece masraf hareketlerinde (arac masrafi, genel gider) calisir.
public class NotAnExpenseException extends ApplicationRuntimeException {
    public NotAnExpenseException() {
        super("Bu kayıt bir masraf değil", ErrorMessageConstant.NOT_AN_EXPENSE);
    }
}
