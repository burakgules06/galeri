package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kurum (Evim / kredi) taksitinde odeyen bir kurum (ORGANIZATION) olmali.
public class InvalidPayerException extends ApplicationRuntimeException {
    public InvalidPayerException() {
        super("Evim ya da kredi taksitinde ödeyen olarak bir kurum seçilmeli", ErrorMessageConstant.INVALID_PAYER);
    }
}
