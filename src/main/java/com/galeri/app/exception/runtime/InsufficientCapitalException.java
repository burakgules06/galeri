package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.response.Shortfall;
import com.galeri.app.util.MoneyText;

// Ortak / konsinye aracta kisinin nakitteki sermayesi (sermaye - araclarda bagli) katkisina yetmiyor:
// kalanini cebinden eklemeli ya da borc almali. data: kisi ve eksik tutar (panel borc sorar).
public class InsufficientCapitalException extends ApplicationRuntimeException {
    public InsufficientCapitalException(PanelUser user, long shortfallCents) {
        super(user.getFullName() + " ortağının nakitteki sermayesi katkıya " + MoneyText.tl(shortfallCents)
                        + " yetmiyor; katkının bir kısmını cepten ekleyin ya da borç alın",
                ErrorMessageConstant.INSUFFICIENT_CAPITAL, new Shortfall(user.getId(), user.getFullName(), shortfallCents));
    }
}
