package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kullaniciyla alis / satis: ya kisi ya kullanici secilir; kullanicida taksit yok, sermaye kismi bedeli asamaz,
// dukkan sahibinin sermaye kismi olmaz (onun parasi havuzdur), devir sadece kullaniciya.
public class InvalidUserCounterpartyException extends ApplicationRuntimeException {
    public InvalidUserCounterpartyException(String message) {
        super(message, ErrorMessageConstant.INVALID_USER_COUNTERPARTY);
    }
}
