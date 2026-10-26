package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Sadece aracsiz (gecmis senet / cek) plan silinir ve hicbir taksiti odenmemis olmali; aracli plan satisi / alisi geri alinarak kalkar.
public class PlanNotRemovableException extends ApplicationRuntimeException {
    public PlanNotRemovableException() {
        super("Bu kayıt silinemez: ödenmiş taksiti var ya da bir araç satışına / alışına bağlı", ErrorMessageConstant.PLAN_NOT_REMOVABLE);
    }
}
