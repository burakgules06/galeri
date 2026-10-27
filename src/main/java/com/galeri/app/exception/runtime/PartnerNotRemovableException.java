package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

public class PartnerNotRemovableException extends ApplicationRuntimeException {
    public PartnerNotRemovableException(String message) {
        super(message, ErrorMessageConstant.PARTNER_NOT_REMOVABLE);
    }
}
