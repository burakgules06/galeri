package com.galeri.app.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneUtilTest {

    @Test
    void farkliYazimlar_ayniStandartNumarayaDonusur() {
        assertThat(PhoneUtil.normalize("5301390726")).isEqualTo("905301390726");
        assertThat(PhoneUtil.normalize("05301390726")).isEqualTo("905301390726");
        assertThat(PhoneUtil.normalize("+90 530 139 07 26")).isEqualTo("905301390726");
        assertThat(PhoneUtil.normalize("00905301390726")).isEqualTo("905301390726");
    }

    @Test
    void bosGirdi_nullDoner() {
        assertThat(PhoneUtil.normalize(null)).isNull();
        assertThat(PhoneUtil.normalize("  -  ")).isNull();
    }

    @Test
    void maskeleme_sonDortHaneyiBirakir() {
        assertThat(PhoneUtil.mask("905301112233")).isEqualTo("********2233");
        assertThat(PhoneUtil.mask(null)).isEqualTo("****");
    }
}
