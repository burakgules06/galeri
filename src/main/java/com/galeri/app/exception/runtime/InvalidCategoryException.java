package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kategori yok, bos ya da masrafin turuyle (arac / genel) uyusmuyor.
public class InvalidCategoryException extends ApplicationRuntimeException {
    public InvalidCategoryException() {
        super("Masraf kategorisi geçersiz", ErrorMessageConstant.INVALID_CATEGORY);
    }
}
