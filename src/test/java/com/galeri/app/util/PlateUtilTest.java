package com.galeri.app.util;

import com.galeri.app.exception.runtime.InvalidPlateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlateUtilTest {

    @Test
    void farkliYazimlar_standartBicimeDonusur() {
        assertThat(PlateUtil.normalize("34abc123")).isEqualTo("34 ABC 123");
        assertThat(PlateUtil.normalize(" 06 tr 7788 ")).isEqualTo("06 TR 7788");
        assertThat(PlateUtil.normalize("42-AF-310")).isEqualTo("42 AF 310");
        assertThat(PlateUtil.normalize("06 b 1234")).isEqualTo("06 B 1234");
    }

    @Test
    void turkceKucukI_buyukIyaCevrilir() {
        assertThat(PlateUtil.normalize("34 kıl 45")).isEqualTo("34 KIL 45");
        assertThat(PlateUtil.normalize("34 kil 45")).isEqualTo("34 KIL 45");
    }

    @Test
    void gecersizBicim_reddedilir() {
        assertThatThrownBy(() -> PlateUtil.normalize("ABC 123")).isInstanceOf(InvalidPlateException.class);
        assertThatThrownBy(() -> PlateUtil.normalize("34 ABCD 12")).isInstanceOf(InvalidPlateException.class);
        assertThatThrownBy(() -> PlateUtil.normalize("34 AB 1")).isInstanceOf(InvalidPlateException.class);
        assertThatThrownBy(() -> PlateUtil.normalize(null)).isInstanceOf(InvalidPlateException.class);
    }

    @Test
    void ilKodu01ile81Disinda_reddedilir() {
        assertThatThrownBy(() -> PlateUtil.normalize("00 ABC 123")).isInstanceOf(InvalidPlateException.class);
        assertThatThrownBy(() -> PlateUtil.normalize("82 ABC 123")).isInstanceOf(InvalidPlateException.class);
    }
}
