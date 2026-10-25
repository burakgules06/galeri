package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Galeride en az bir aktif yonetici kalmali; yoksa kimse kullanici yonetemez.
public class LastActiveAdminException extends ApplicationRuntimeException {
    public LastActiveAdminException() {
        super("Galeride en az bir aktif yönetici kalmalı", ErrorMessageConstant.LAST_ACTIVE_ADMIN);
    }
}
