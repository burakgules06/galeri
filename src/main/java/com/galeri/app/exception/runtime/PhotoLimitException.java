package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Arac basina fotograf siniri (vitrin sayfasi ve disk icin).
public class PhotoLimitException extends ApplicationRuntimeException {
    public PhotoLimitException() {
        super("Bir araca en fazla 20 fotoğraf eklenebilir.", ErrorMessageConstant.PHOTO_LIMIT);
    }
}
