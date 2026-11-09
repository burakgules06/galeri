package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Hareketi olan banka hesabi ancak bakiyesi 0 iken kaldirilir (arsivlenir); para baska hesaba aktarilmadan kaldirilamaz.
public class BankAccountNotRemovableException extends ApplicationRuntimeException {
    public BankAccountNotRemovableException() {
        super("Bu hesapta para var. Kaldırmadan önce parayı başka bir hesaba aktarın.", ErrorMessageConstant.BANK_ACCOUNT_NOT_REMOVABLE);
    }
}
