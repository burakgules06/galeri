package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Sermayesi, araci ya da evraki olan sermayedar calisan yapilamaz (bir kullanicinin tek rolu var; V22).
public class RoleChangeNotAllowedException extends ApplicationRuntimeException {
    public RoleChangeNotAllowedException() {
        super("Sermayesi ya da aracı olan kullanıcı çalışan yapılamaz", ErrorMessageConstant.ROLE_CHANGE_NOT_ALLOWED);
    }
}
