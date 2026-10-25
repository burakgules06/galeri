package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Yonetici kendini pasiflestiremez ve kendi rolunu dusuremez (baska bir yonetici yapmali).
public class SelfChangeNotAllowedException extends ApplicationRuntimeException {
    public SelfChangeNotAllowedException() {
        super("Kendi hesabınızı pasifleştiremez ya da rolünüzü düşüremezsiniz", ErrorMessageConstant.SELF_CHANGE_NOT_ALLOWED);
    }
}
