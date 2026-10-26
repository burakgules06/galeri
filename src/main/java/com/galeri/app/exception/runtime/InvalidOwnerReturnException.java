package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ortaga donus: kisi aracin ortagi olmali, nakit kisim alacagini (katki + kar payi) asamaz, kasa / banka secilmeli.
public class InvalidOwnerReturnException extends ApplicationRuntimeException {
    public InvalidOwnerReturnException() {
        super("Ortağa ödenecek tutar alacağından büyük olamaz; kasa ya da banka seçin", ErrorMessageConstant.INVALID_OWNER_RETURN);
    }
}
