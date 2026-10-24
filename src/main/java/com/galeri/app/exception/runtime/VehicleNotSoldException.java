package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Satilmamis aracin satisi geri alinamaz.
public class VehicleNotSoldException extends ApplicationRuntimeException {
    public VehicleNotSoldException() {
        super("Bu araç satılmamış", ErrorMessageConstant.VEHICLE_NOT_SOLD);
    }
}
