package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Duzeltme hareketi tekrar duzeltilmez; gerekiyorsa yeni hareket girilir.
public class DuzeltmeDuzeltilemezException extends ApplicationRuntimeException {
    public DuzeltmeDuzeltilemezException() {
        super("Düzeltme hareketi tekrar düzeltilemez", ErrorMessageConstant.DUZELTME_DUZELTILEMEZ);
    }
}
