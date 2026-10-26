package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ortak aracta ortagin nakitteki sermayesi (sermaye - araclarda bagli) katkisina yetmiyor: kalanini cebinden eklemeli.
public class InsufficientCapitalException extends ApplicationRuntimeException {
    public InsufficientCapitalException(String partnerName) {
        super(partnerName + " ortağının nakitteki sermayesi katkıya yetmiyor; katkının bir kısmını cepten ekleyin",
                ErrorMessageConstant.INSUFFICIENT_CAPITAL);
    }
}
