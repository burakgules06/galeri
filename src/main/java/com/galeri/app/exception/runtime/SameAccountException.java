package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Virmanda kaynak ve hedef hesap ayni olamaz.
public class SameAccountException extends ApplicationRuntimeException {
    public SameAccountException() {
        super("Virmanda iki farklı hesap seçilmeli", ErrorMessageConstant.SAME_ACCOUNT);
    }
}
