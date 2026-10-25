package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Konsinye aracta sahibi ve komisyon (oran ya da sabit tutar) zorunlu.
public class ConsignmentDetailsRequiredException extends ApplicationRuntimeException {
    public ConsignmentDetailsRequiredException() {
        super("Konsinye araçta aracın sahibi ve komisyon girilmeli", ErrorMessageConstant.CONSIGNMENT_DETAILS_REQUIRED);
    }
}
