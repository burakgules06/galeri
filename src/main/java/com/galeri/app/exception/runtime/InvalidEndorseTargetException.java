package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ciro ile odenecek taksit: ayni kisiye, odenmemis alis taksiti ve tutari cekten az degil.
public class InvalidEndorseTargetException extends ApplicationRuntimeException {
    public InvalidEndorseTargetException() {
        super("Seçilen ödeme bu kişiye ait açık bir ödeme değil ya da tutarı çekten küçük", ErrorMessageConstant.INVALID_ENDORSE_TARGET);
    }
}
