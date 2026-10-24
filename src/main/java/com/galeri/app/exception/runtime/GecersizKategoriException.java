package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Kategori yok, bos ya da masrafin turuyle (arac / genel) uyusmuyor.
public class GecersizKategoriException extends ApplicationRuntimeException {
    public GecersizKategoriException() {
        super("Masraf kategorisi geçersiz", ErrorMessageConstant.GECERSIZ_KATEGORI);
    }
}
