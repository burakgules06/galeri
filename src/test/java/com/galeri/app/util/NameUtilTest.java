package com.galeri.app.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameUtilTest {

    @Test
    void tamamiBuyukYazilmisAd_kelimeBaslariBuyukOlur() {
        assertThat(NameUtil.normalize("ALİ YILMAZ")).isEqualTo("Ali Yılmaz");
        assertThat(NameUtil.normalize("IŞIK İNCE")).isEqualTo("Işık İnce");
    }

    @Test
    void tamamiKucukYazilmisAd_turkceHarflerleDuzelir() {
        assertThat(NameUtil.normalize("ismail ışık")).isEqualTo("İsmail Işık");
        assertThat(NameUtil.normalize("  ayşe-nur   çelik ")).isEqualTo("Ayşe-Nur Çelik");
    }

    @Test
    void karisikYazilmisMetne_dokunulmaz() {
        assertThat(NameUtil.normalize("BMW Yetkili Servisi")).isEqualTo("BMW Yetkili Servisi");
        assertThat(NameUtil.normalize("McLaren")).isEqualTo("McLaren");
    }

    @Test
    void rakamVeNoktalarlaBozulmaz() {
        assertThat(NameUtil.normalize("ÖZ OTO LTD.ŞTİ.")).isEqualTo("Öz Oto Ltd.Şti.");
        assertThat(NameUtil.normalize("2. el")).isEqualTo("2. El");
        assertThat(NameUtil.normalize("   ")).isEmpty();
        assertThat(NameUtil.normalize(null)).isNull();
    }
}
