package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Bir hareket en fazla bir kez ters hareketle duzeltilir.
public class HareketZatenDuzeltildiException extends ApplicationRuntimeException {
    public HareketZatenDuzeltildiException() {
        super("Bu hareket zaten düzeltilmiş", ErrorMessageConstant.HAREKET_ZATEN_DUZELTILDI);
    }
}
