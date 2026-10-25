package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ortakli aracta paylar toplami %100 olmali ve her kisi bir kez yazilmali.
public class InvalidSharesException extends ApplicationRuntimeException {
    public InvalidSharesException() {
        super("Ortak payları toplamı %100 olmalı ve her kişi bir kez yazılmalı", ErrorMessageConstant.INVALID_SHARES);
    }
}
