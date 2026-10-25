package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Acilis bakiyesi hesap basina bir kez girilir; yanlissa Kasa'dan "Duzelt" ile terslenip yeniden girilir.
public class OpeningBalanceExistsException extends ApplicationRuntimeException {
    public OpeningBalanceExistsException() {
        super("Bu hesabın açılış bakiyesi zaten girilmiş; yanlışsa önce Kasa'dan düzeltin", ErrorMessageConstant.OPENING_BALANCE_EXISTS);
    }
}
