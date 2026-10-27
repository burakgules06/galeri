package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Toplu satis: en az iki farkli, stokta arac olmali.
public class InvalidBasketException extends ApplicationRuntimeException {
    public InvalidBasketException() {
        super("Toplu satışta en az iki farklı ve stokta olan araç olmalı", ErrorMessageConstant.INVALID_BASKET);
    }
}
