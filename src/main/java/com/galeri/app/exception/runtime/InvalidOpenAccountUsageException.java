package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Acik hesap yalnizca aracsiz gecmis cari kaydinda kullanilir.
public class InvalidOpenAccountUsageException extends ApplicationRuntimeException {
    public InvalidOpenAccountUsageException() {
        super("Açık hesap yöntemi yalnızca geçmiş cari kaydında kullanılabilir", ErrorMessageConstant.INVALID_OPEN_ACCOUNT_USAGE);
    }
}
