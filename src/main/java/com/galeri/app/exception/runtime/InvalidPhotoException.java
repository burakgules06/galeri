package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Yuklenen dosya resim olarak okunamadi (bozuk ya da desteklenmeyen tur).
public class InvalidPhotoException extends ApplicationRuntimeException {
    public InvalidPhotoException() {
        super("Fotoğraf okunamadı. JPG, PNG ya da WEBP bir resim seç.", ErrorMessageConstant.INVALID_PHOTO);
    }
}
