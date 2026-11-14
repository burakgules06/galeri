package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.response.Shortfall;
import com.galeri.app.util.MoneyText;

// Sermaye cekisi / aktarimi ortagin nakitteki sermayesinden buyuk olamaz (eksi sermaye paylari bozar).
// data: kisi ve eksik tutar (panel borc sorar).
public class CapitalExceededException extends ApplicationRuntimeException {
    public CapitalExceededException(PanelUser user, long shortfallCents) {
        super("Tutar " + user.getFullName() + " ortağının nakitteki sermayesinden " + MoneyText.tl(shortfallCents) + " fazla",
                ErrorMessageConstant.CAPITAL_EXCEEDED, new Shortfall(user.getId(), user.getFullName(), shortfallCents));
    }
}
