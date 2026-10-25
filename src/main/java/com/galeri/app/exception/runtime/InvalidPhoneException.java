package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kullanici telefonu gecerli bir cep telefonu olmali (giris bununla yapilir).
public class InvalidPhoneException extends ApplicationRuntimeException {
    public InvalidPhoneException() {
        super("Geçerli bir cep telefonu girin (0530 111 22 33 gibi)", ErrorMessageConstant.INVALID_PHONE);
    }
}
