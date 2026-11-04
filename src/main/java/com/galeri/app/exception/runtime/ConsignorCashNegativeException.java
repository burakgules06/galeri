package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Konsinye araca masraf: sahibinin nakitteki parasi yetmiyor. Panel "eksiye dusecek, devam?" diye sorar; onaylanirsa
// istek confirmNegative ile tekrar gelir.
public class ConsignorCashNegativeException extends ApplicationRuntimeException {
    public ConsignorCashNegativeException(String ownerName) {
        super(ownerName + " adlı sermayedarın nakitteki parası eksiye düşecek. Devam edilsin mi?",
                ErrorMessageConstant.CONSIGNOR_CASH_NEGATIVE);
    }
}
