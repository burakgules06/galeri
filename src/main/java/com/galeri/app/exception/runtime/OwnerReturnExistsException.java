package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ortak aracin satisi geri alinamaz: bu satis icin ortaga nakit donus (sermaye cekisi) yapilmis; once o geri alinmali.
public class OwnerReturnExistsException extends ApplicationRuntimeException {
    public OwnerReturnExistsException() {
        super("Bu satış için ortağa nakit ödeme yapılmış; önce Kasa'dan o sermaye çekişini düzeltin",
                ErrorMessageConstant.OWNER_RETURN_EXISTS);
    }
}
