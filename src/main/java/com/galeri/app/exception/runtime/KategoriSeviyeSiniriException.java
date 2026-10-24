package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Masraf kategorileri en fazla 3 seviye olur (bkz. docs/MIMARI.md §7).
public class KategoriSeviyeSiniriException extends ApplicationRuntimeException {
    public KategoriSeviyeSiniriException() {
        super("Kategoriler en fazla 3 seviye olabilir", ErrorMessageConstant.KATEGORI_SEVIYE_SINIRI);
    }
}
