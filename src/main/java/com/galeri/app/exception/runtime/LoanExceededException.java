package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Borc verirken verenin o hesapta / nakitte o kadar parasi yok; geri odemede borclunun parasi yetmiyor ya da tutar kalan
// borcu asiyor. Mesaj nedeni soyler.
public class LoanExceededException extends ApplicationRuntimeException {
    public LoanExceededException(String message) {
        super(message, ErrorMessageConstant.LOAN_EXCEEDED);
    }
}
