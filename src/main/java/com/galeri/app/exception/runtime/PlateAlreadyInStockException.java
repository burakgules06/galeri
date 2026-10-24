package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ayni galeride ayni plaka ayni anda iki kez IN_STOCK olamaz.
public class PlateAlreadyInStockException extends ApplicationRuntimeException {
    public PlateAlreadyInStockException() {
        super("Bu plakayla stokta zaten bir araç var", ErrorMessageConstant.PLATE_ALREADY_IN_STOCK);
    }
}
