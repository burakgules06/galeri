package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Donem baslangici bitisinden sonra olamaz.
public class InvalidPeriodException extends ApplicationRuntimeException {
    public InvalidPeriodException() {
        super("Dönem başlangıcı bitişinden sonra olamaz", ErrorMessageConstant.INVALID_PERIOD);
    }
}
