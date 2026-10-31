package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Panele girebilecek kullanicinin telefonu ve sifresi olmali (V22).
public class LoginDetailsRequiredException extends ApplicationRuntimeException {
    public LoginDetailsRequiredException() {
        super("Panele girebilmesi için telefon ve şifre girin", ErrorMessageConstant.LOGIN_DETAILS_REQUIRED);
    }
}
