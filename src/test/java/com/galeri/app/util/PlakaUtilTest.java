package com.galeri.app.util;

import com.galeri.app.exception.runtime.GecersizPlakaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlakaUtilTest {

    @Test
    void farkliYazimlar_standartBicimeDonusur() {
        assertThat(PlakaUtil.normalize("34abc123")).isEqualTo("34 ABC 123");
        assertThat(PlakaUtil.normalize(" 06 tr 7788 ")).isEqualTo("06 TR 7788");
        assertThat(PlakaUtil.normalize("42-AF-310")).isEqualTo("42 AF 310");
        assertThat(PlakaUtil.normalize("06 b 1234")).isEqualTo("06 B 1234");
    }

    @Test
    void turkceKucukI_buyukIyaCevrilir() {
        assertThat(PlakaUtil.normalize("34 kıl 45")).isEqualTo("34 KIL 45");
        assertThat(PlakaUtil.normalize("34 kil 45")).isEqualTo("34 KIL 45");
    }

    @Test
    void gecersizBicim_reddedilir() {
        assertThatThrownBy(() -> PlakaUtil.normalize("ABC 123")).isInstanceOf(GecersizPlakaException.class);
        assertThatThrownBy(() -> PlakaUtil.normalize("34 ABCD 12")).isInstanceOf(GecersizPlakaException.class);
        assertThatThrownBy(() -> PlakaUtil.normalize("34 AB 1")).isInstanceOf(GecersizPlakaException.class);
        assertThatThrownBy(() -> PlakaUtil.normalize(null)).isInstanceOf(GecersizPlakaException.class);
    }

    @Test
    void ilKodu01ile81Disinda_reddedilir() {
        assertThatThrownBy(() -> PlakaUtil.normalize("00 ABC 123")).isInstanceOf(GecersizPlakaException.class);
        assertThatThrownBy(() -> PlakaUtil.normalize("82 ABC 123")).isInstanceOf(GecersizPlakaException.class);
    }
}
