package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Odenmemis taksitin tahsilati geri alinamaz.
public class InstallmentNotPaidException extends ApplicationRuntimeException {
    public InstallmentNotPaidException() {
        super("Bu taksit henüz ödenmemiş", ErrorMessageConstant.INSTALLMENT_NOT_PAID);
    }
}
