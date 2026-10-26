package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Dukkan payi sadece ortak aracta; oranda baz puan, sabitte tutar girilmeli.
public class InvalidShopCutException extends ApplicationRuntimeException {
    public InvalidShopCutException() {
        super("Dükkân payı sadece ortak araçta; oran ya da tutar girin", ErrorMessageConstant.INVALID_SHOP_CUT);
    }
}
