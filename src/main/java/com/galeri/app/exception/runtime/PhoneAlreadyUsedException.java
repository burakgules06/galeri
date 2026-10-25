package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Telefon giris kimligidir; tum sistemde tekil olmali.
public class PhoneAlreadyUsedException extends ApplicationRuntimeException {
    public PhoneAlreadyUsedException() {
        super("Bu telefonla kayıtlı bir kullanıcı zaten var", ErrorMessageConstant.PHONE_ALREADY_USED);
    }
}
