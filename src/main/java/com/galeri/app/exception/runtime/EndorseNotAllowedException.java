package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ciro sadece alinmis (satis planindaki), odenmemis cek / senette olur.
public class EndorseNotAllowedException extends ApplicationRuntimeException {
    public EndorseNotAllowedException() {
        super("Bu taksit ciro edilemez: sadece ödenmemiş, alınmış çek ya da senet ciro edilir", ErrorMessageConstant.ENDORSE_NOT_ALLOWED);
    }
}
