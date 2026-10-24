package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Satilmis arac ikinci kez satilamaz.
public class VehicleAlreadySoldException extends ApplicationRuntimeException {
    public VehicleAlreadySoldException() {
        super("Bu araç zaten satılmış", ErrorMessageConstant.VEHICLE_ALREADY_SOLD);
    }
}
