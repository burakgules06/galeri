package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Sifre degistirirken mevcut sifre yanlis girildi.
public class WrongPasswordException extends ApplicationRuntimeException {
    public WrongPasswordException() {
        super("Mevcut şifre hatalı", ErrorMessageConstant.WRONG_PASSWORD);
    }
}
