package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Karsiliksiz sadece alinmis cek / senette.
public class BounceNotAllowedException extends ApplicationRuntimeException {
    public BounceNotAllowedException() {
        super("Sadece alınmış çek ya da senet karşılıksız olarak işaretlenebilir", ErrorMessageConstant.BOUNCE_NOT_ALLOWED);
    }
}
