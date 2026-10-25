package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kar payi ya da dukkan sahipligi sadece sermaye ortagina (CAPITAL hesabi olan kisi) verilir.
public class NotAPartnerException extends ApplicationRuntimeException {
    public NotAPartnerException() {
        super("Bu kişi sermaye ortağı değil; önce ortak yapın", ErrorMessageConstant.NOT_A_PARTNER);
    }
}
