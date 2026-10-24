package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Satisa ya da taksite bagli hareket kasa ekranindan duzeltilmez; plan bozulmasin diye
// satis arac detayindan, tahsilat odemeler ekranindan geri alinir.
public class LinkedEntryException extends ApplicationRuntimeException {
    public LinkedEntryException() {
        super("Bu hareket bir satışa ya da taksite bağlı. Satışı araç detayından, tahsilatı ödemeler ekranından geri alın.",
                ErrorMessageConstant.LINKED_ENTRY);
    }
}
