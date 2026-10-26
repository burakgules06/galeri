package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

public class PushDeliveryFailedException extends ApplicationRuntimeException {
    public PushDeliveryFailedException() {
        super("Bildirim gönderilemedi. Tekrar deneyin.", ErrorMessageConstant.PUSH_DELIVERY_FAILED);
    }
}
