package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Odenmis taksit tekrar tahsil edilemez ve duzenlenemez.
public class InstallmentAlreadyPaidException extends ApplicationRuntimeException {
    public InstallmentAlreadyPaidException() {
        super("Bu taksit zaten ödenmiş", ErrorMessageConstant.INSTALLMENT_ALREADY_PAID);
    }
}
