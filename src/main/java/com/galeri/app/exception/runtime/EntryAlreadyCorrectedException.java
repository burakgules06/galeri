package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Bir hareket en fazla bir kez ters hareketle duzeltilir.
public class EntryAlreadyCorrectedException extends ApplicationRuntimeException {
    public EntryAlreadyCorrectedException() {
        super("Bu hareket zaten düzeltilmiş", ErrorMessageConstant.ENTRY_ALREADY_CORRECTED);
    }
}
