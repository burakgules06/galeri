package com.galeri.app.model.enums;

// Sermayedarin havuzdaki yuzdesinin (sadece bilgi, hesapta kullanilmaz) neye gore gosterilecegi. Degerler V16 CHECK ile ayni.
// ALL: tum varlik (kasa + bankalar + araclar + alacaklar - borclar), VEHICLES: araclardaki para, CASH: kasa + bankalar.
public enum CapitalBase {
    ALL, VEHICLES, CASH
}
