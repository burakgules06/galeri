package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kismi tahsilat: tutar taksitten kucuk olmali, cek bolunemez.
public class InvalidPartialAmountException extends ApplicationRuntimeException {
    public InvalidPartialAmountException() {
        super("Kısmi tutar taksitten küçük olmalı; çek bölünerek tahsil edilemez", ErrorMessageConstant.INVALID_PARTIAL_AMOUNT);
    }
}
