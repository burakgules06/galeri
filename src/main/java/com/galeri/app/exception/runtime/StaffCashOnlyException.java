package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Yetkili calisan sadece pesin (galeri adina, tamami simdi kasa / bankadan) alim ve satim girebilir.
public class StaffCashOnlyException extends ApplicationRuntimeException {
    public StaffCashOnlyException() {
        super("Çalışan sadece peşin alım / satım girebilir (vadeli, konsinye ve ortaklı işlemleri yönetici girer)",
                ErrorMessageConstant.STAFF_CASH_ONLY);
    }
}
