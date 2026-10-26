package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

public class PushUnavailableException extends ApplicationRuntimeException {
    public PushUnavailableException() {
        super("Bu cihazda bildirim açık değil", ErrorMessageConstant.PUSH_UNAVAILABLE);
    }
}
