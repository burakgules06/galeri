package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Alis planinda elden verilen cek / senet: plan satirinin tutari evrakin tutariyla birebir ayni olmali.
public class EndorseAmountMismatchException extends ApplicationRuntimeException {
    public EndorseAmountMismatchException() {
        super("Elden verilen çek / senedin tutarı plan satırıyla aynı olmalı", ErrorMessageConstant.ENDORSE_AMOUNT_MISMATCH);
    }
}
