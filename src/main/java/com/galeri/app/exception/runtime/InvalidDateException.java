package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Satis tarihi alis tarihinden once.
public class InvalidDateException extends ApplicationRuntimeException {
    public InvalidDateException() {
        super("Satış tarihi alış tarihinden önce olamaz", ErrorMessageConstant.INVALID_DATE);
    }
}
