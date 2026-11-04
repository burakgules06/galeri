package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Cepten masraf sadece konsinye araca girilir (takip icin; parasal etkisi yok).
public class PocketExpenseNotAllowedException extends ApplicationRuntimeException {
    public PocketExpenseNotAllowedException() {
        super("Cepten masraf sadece konsinye araca girilir.", ErrorMessageConstant.POCKET_EXPENSE_NOT_ALLOWED);
    }
}
