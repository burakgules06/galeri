package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Planli (senetli / taksitli) alista borc saticinin carisine yazilir; satici secilmeli.
public class SellerRequiredException extends ApplicationRuntimeException {
    public SellerRequiredException() {
        super("Senetli ya da taksitli alışta kimden alındığı seçilmeli", ErrorMessageConstant.SELLER_REQUIRED);
    }
}
