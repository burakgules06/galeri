package com.galeri.app.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMathTest {

    @Test
    void agirligaGoreBolme_toplamTutaraEsit_kalanBuyukAgirliga() {
        assertThat(MoneyMath.splitByWeights(100L, List.of(1L, 2L))).containsExactly(33L, 67L);
        assertThat(MoneyMath.splitByWeights(1000000L, List.of(6300000L, 4100000L))).containsExactly(605770L, 394230L);
        assertThat(MoneyMath.splitByWeights(-100L, List.of(1L, 2L)).stream().mapToLong(Long::longValue).sum()).isEqualTo(-100L);
        assertThat(MoneyMath.splitByWeights(-100L, List.of(1L, 2L))).containsExactly(-34L, -66L);
    }

    @Test
    void buyukTutarVeSermaye_tasmaz_agirlikYoksaSifir() {
        long big = 500_000_000_000_00L;   // 500 milyar TL
        assertThat(MoneyMath.splitByWeights(big, List.of(big, big)).stream().mapToLong(Long::longValue).sum()).isEqualTo(big);
        assertThat(MoneyMath.splitByWeights(100L, List.of(0L, 0L))).containsExactly(0L, 0L);
        assertThat(MoneyMath.basisPoints(6500000L, 10000000L)).isEqualTo(6500);
        assertThat(MoneyMath.basisPoints(1L, 0L)).isZero();
    }
}
