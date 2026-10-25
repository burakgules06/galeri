package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Sermaye cekisi ortagin mevcut sermayesinden buyuk olamaz (eksi sermaye paylari bozar).
public class CapitalExceededException extends ApplicationRuntimeException {
    public CapitalExceededException() {
        super("Çekilen tutar ortağın sermayesinden büyük olamaz", ErrorMessageConstant.CAPITAL_EXCEEDED);
    }
}
