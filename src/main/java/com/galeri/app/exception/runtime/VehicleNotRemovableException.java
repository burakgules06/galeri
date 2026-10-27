package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

public class VehicleNotRemovableException extends ApplicationRuntimeException {
    public VehicleNotRemovableException(String message) {
        super(message, ErrorMessageConstant.VEHICLE_NOT_REMOVABLE);
    }
}
