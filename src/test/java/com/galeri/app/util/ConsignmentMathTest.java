package com.galeri.app.util;

import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.CommissionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsignmentMathTest {

    private static Vehicle rate(int bp) {
        Vehicle v = new Vehicle();
        v.setCommissionType(CommissionType.RATE);
        v.setCommissionRateBp(bp);
        return v;
    }

    @Test
    void oranliKomisyon_kurusaYarimYukariYuvarlanir() {
        assertThat(ConsignmentMath.commission(rate(300), 130000000L)).isEqualTo(3900000L);   // 1.300.000 TL'nin %3'u = 39.000 TL
        assertThat(ConsignmentMath.commission(rate(150), 1050L)).isEqualTo(16L);              // 10,50 TL * %1,5 = 0,1575 -> 0,16
        assertThat(ConsignmentMath.commission(rate(150), 1030L)).isEqualTo(15L);              // 0,1545 -> 0,15
        assertThat(ConsignmentMath.commission(rate(250), 1000L)).isEqualTo(25L);              // tam bolunen
        assertThat(ConsignmentMath.commission(rate(0), 130000000L)).isZero();
    }

    @Test
    void sabitKomisyon_satistanBagimsiz_oranliSatisYoksaBilinmez() {
        Vehicle fixed = new Vehicle();
        fixed.setCommissionType(CommissionType.FIXED);
        fixed.setCommissionCents(2500000L);

        assertThat(ConsignmentMath.commission(fixed, null)).isEqualTo(2500000L);
        assertThat(ConsignmentMath.commission(fixed, 130000000L)).isEqualTo(2500000L);
        assertThat(ConsignmentMath.commission(rate(300), null)).isNull();
        assertThat(ConsignmentMath.commission(new Vehicle(), 100L)).isNull();   // konsinye degil
    }

    @Test
    void maliyetGirildiyse_komisyonKarinYuzdesi() {
        Vehicle v = rate(300);
        v.setActualPurchaseCents(80_000_000L);   // 800.000 TL

        // satis 1.000.000, kar 200.000, %3 -> galeri 6.000 TL
        assertThat(ConsignmentMath.commission(v, 100_000_000L)).isEqualTo(600_000L);
    }

    @Test
    void zarardaVeKarsizSatista_komisyonSifir() {
        Vehicle oran = rate(300);
        oran.setActualPurchaseCents(80_000_000L);
        Vehicle sabit = new Vehicle();
        sabit.setCommissionType(CommissionType.FIXED);
        sabit.setCommissionCents(2_500_000L);
        sabit.setActualPurchaseCents(80_000_000L);

        assertThat(ConsignmentMath.commission(oran, 70_000_000L)).isZero();
        assertThat(ConsignmentMath.commission(oran, 80_000_000L)).isZero();
        assertThat(ConsignmentMath.commission(sabit, 70_000_000L)).isZero();
    }

    @Test
    void sabitKomisyon_kardanBuyukse_karKadar() {
        Vehicle sabit = new Vehicle();
        sabit.setCommissionType(CommissionType.FIXED);
        sabit.setCommissionCents(500_000L);       // 5.000 TL
        sabit.setActualPurchaseCents(99_700_000L);

        assertThat(ConsignmentMath.commission(sabit, 100_000_000L)).isEqualTo(300_000L);   // kar 3.000 TL
    }

    @Test
    void karBolme_toplamHepTutaraEsit_zarardaDa() {
        assertThat(ConsignmentMath.split(10000001L, List.of(5000, 5000))).containsExactly(5000001L, 5000000L);
        assertThat(ConsignmentMath.split(100L, List.of(3334, 3333, 3333))).containsExactly(34L, 33L, 33L);
        List<Long> loss = ConsignmentMath.split(-100L, List.of(6000, 4000));
        assertThat(loss).containsExactly(-60L, -40L);
        assertThat(ConsignmentMath.split(-101L, List.of(5000, 5000)).stream().mapToLong(Long::longValue).sum()).isEqualTo(-101L);
    }
}
