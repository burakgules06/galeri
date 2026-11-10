package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kaldirilan (arsivlenen) ve parasi olmayan banka hesabina islem yazilmaz.
public class ClosedAccountException extends ApplicationRuntimeException {
    public ClosedAccountException(String accountName) {
        super(accountName + " hesabı kaldırılmış; başka bir hesap seçin", ErrorMessageConstant.CLOSED_ACCOUNT);
    }
}
