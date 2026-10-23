package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Istenen kayit mevcut ama baska bir isletmeye ait. Disariya "bulunamadi" gibi davranir.
public class SourceNotBelongToTenantException extends ApplicationRuntimeException {
    public SourceNotBelongToTenantException() {
        super("Kayıt bulunamadı", ErrorMessageConstant.SOURCE_NOT_BELONG_TO_TENANT);
    }
}
