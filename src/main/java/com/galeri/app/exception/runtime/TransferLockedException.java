package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// "Satmak icin al" ile acilan kayit: alis bilgisi devirden gelir; once eski aracin satisini geri al.
public class TransferLockedException extends ApplicationRuntimeException {
    public TransferLockedException(String message) {
        super(message, ErrorMessageConstant.TRANSFER_LOCKED);
    }
}
