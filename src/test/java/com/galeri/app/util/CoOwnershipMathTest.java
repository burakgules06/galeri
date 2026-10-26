package com.galeri.app.util;

import com.galeri.app.model.enums.CommissionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoOwnershipMathTest {

    @Test
    void ucOrtak_yuzde3334_3333_3333_kurusKalaniEnBuyugeGider_toplamTutar() {
        List<Long> parts = CoOwnershipMath.split(100_000_01L, List.of(3334, 3333, 3333), -1);

        assertThat(parts).containsExactly(33_340_01L, 33_330_00L, 33_330_00L);
        assertThat(parts.stream().mapToLong(Long::longValue).sum()).isEqualTo(100_000_01L);
    }

    @Test
    void havuzVarsa_ortaklarAsagiYuvarlanir_kurusFarkiHavuza() {
        // 1.000.000,01 TL, Havuz %50 + ortak %50: ortak 500.000,00, havuz 500.000,01
        List<Long> parts = CoOwnershipMath.split(100_000_001L, List.of(5000, 5000), 0);

        assertThat(parts).containsExactly(50_000_001L, 50_000_000L);
    }

    @Test
    void zarardaDa_toplamTutar_ortakAsagiYuvarlanir() {
        List<Long> parts = CoOwnershipMath.split(-1001L, List.of(3333, 6667), 0);   // havuz %33,33, ortak %66,67

        assertThat(parts.get(1)).isEqualTo(-668L);   // -667,3667 asagi
        assertThat(parts.stream().mapToLong(Long::longValue).sum()).isEqualTo(-1001L);
    }

    @Test
    void dukkanPayi_oranYarimYukari_sabitEnFazlaKarKadar_zarardaYok() {
        assertThat(CoOwnershipMath.shopCut(15_000_000L, CommissionType.RATE, 1000, null)).isEqualTo(1_500_000L);
        assertThat(CoOwnershipMath.shopCut(5L, CommissionType.RATE, 1000, null)).isEqualTo(1L);   // 0,5 kurus -> 1
        assertThat(CoOwnershipMath.shopCut(3_000_000L, CommissionType.FIXED, null, 5_000_000L)).isEqualTo(3_000_000L);
        assertThat(CoOwnershipMath.shopCut(-100L, CommissionType.FIXED, null, 50L)).isZero();
        assertThat(CoOwnershipMath.shopCut(100L, null, null, null)).isZero();
    }
}
