package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Yeni ya da degisen arac kaydinda marka / model katalogda olmali; degilse "Listede yok" ile kataloga eklenir.
public class CatalogUnknownException extends ApplicationRuntimeException {
    public CatalogUnknownException() {
        super("Marka ya da model katalogda yok; \"Listede yok\" ile kataloğa ekleyebilirsiniz", ErrorMessageConstant.CATALOG_UNKNOWN);
    }
}
