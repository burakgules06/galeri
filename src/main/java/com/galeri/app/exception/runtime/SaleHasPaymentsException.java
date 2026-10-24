package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Tahsil edilmis taksiti olan satis geri alinamaz; once tahsilatlar geri alinir.
public class SaleHasPaymentsException extends ApplicationRuntimeException {
    public SaleHasPaymentsException() {
        super("Tahsil edilmiş taksiti olan satış geri alınamaz. Önce tahsilatları geri alın.", ErrorMessageConstant.SALE_HAS_PAYMENTS);
    }
}
