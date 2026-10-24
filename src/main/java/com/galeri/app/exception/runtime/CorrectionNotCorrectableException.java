package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Duzeltme hareketi tekrar duzeltilmez; gerekiyorsa yeni hareket girilir.
public class CorrectionNotCorrectableException extends ApplicationRuntimeException {
    public CorrectionNotCorrectableException() {
        super("Düzeltme hareketi tekrar düzeltilemez", ErrorMessageConstant.CORRECTION_NOT_CORRECTABLE);
    }
}
