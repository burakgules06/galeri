package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Sermayedarin parasini hesaplar arasi tasirken: kaynak hesapta o kadar parasi yok.
public class HolderTransferExceededException extends ApplicationRuntimeException {
    public HolderTransferExceededException() {
        super("Bu hesapta o kadar parası yok", ErrorMessageConstant.HOLDER_TRANSFER_EXCEEDED);
    }
}
