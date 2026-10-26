package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Ortak aracta: en az bir ortak, Havuz en fazla bir kez, her kisi bir kez; sahiplik paylari ve kar payi oranlari
// ayri ayri toplam %100; hibrit katkida cepten tutar girilmeli.
public class InvalidSharesException extends ApplicationRuntimeException {
    public InvalidSharesException() {
        super("Sahiplik payları ve kâr payı oranları ayrı ayrı %100 olmalı, her sahip bir kez yazılmalı",
                ErrorMessageConstant.INVALID_SHARES);
    }
}
