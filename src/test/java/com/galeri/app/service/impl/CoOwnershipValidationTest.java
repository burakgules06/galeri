package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InvalidSharesException;
import com.galeri.app.model.enums.ContributionSource;
import com.galeri.app.model.request.ShareRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Ortak aracin sahip satirlari (CoOwnershipServiceImpl.validate)
class CoOwnershipValidationTest {

    // o: dukkan sahibi (payi eski Havuz gibi), a ve b: sermayedar (V22)
    private final UUID o = UUID.randomUUID();
    private final UUID o2 = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private void valid(List<ShareRequest> owners, long price) {
        CoOwnershipServiceImpl.validate(owners, price, owners.stream().map(r -> r.userId() == o || r.userId() == o2).toList());
    }

    @Test
    void sahipVeSermayedar_yuzde50_gecerli_karPayiOraniFarkliOlabilir() {
        assertThatCode(() -> valid(List.of(new ShareRequest(o, 5000), new ShareRequest(a, 5000)), 200_000_000L))
                .doesNotThrowAnyException();
        assertThatCode(() -> valid(List.of(
                new ShareRequest(o, 5000, 4000, null, null), new ShareRequest(a, 5000, 6000, null, null)), 200_000_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void paylarYaDaKarPayiOranlari100DegilseYaDaTekrarliysa_reddedilir() {
        List<List<ShareRequest>> wrong = List.of(
                List.of(new ShareRequest(a, 5000), new ShareRequest(b, 4000)),                                    // eksik
                List.of(new ShareRequest(a, 6000), new ShareRequest(b, 5000)),                                    // fazla
                List.of(new ShareRequest(a, 5000), new ShareRequest(a, 5000)),                                    // kisi tekrar
                List.of(new ShareRequest(o, 5000), new ShareRequest(o2, 5000)),                                   // sadece dukkan sahipleri
                List.of(new ShareRequest(o, 10000)),                                                              // sadece dukkan sahibi
                List.of(new ShareRequest(null, 5000), new ShareRequest(a, 5000)),                                 // sahipsiz satir
                List.of(new ShareRequest(a, 5000, 7000, null, null), new ShareRequest(b, 5000, 5000, null, null)), // kar payi %120
                List.of());
        for (List<ShareRequest> owners : wrong) {
            assertThatThrownBy(() -> valid(owners, 100_000_000L)).isInstanceOf(InvalidSharesException.class);
        }
    }

    @Test
    void hibrit_ceptenTutarZorunlu_katkiyiAsamaz_sahipCeptenOlamaz() {
        long price = 200_000_000L;   // katki 1.000.000
        assertThatCode(() -> valid(List.of(new ShareRequest(o, 5000),
                new ShareRequest(a, 5000, null, ContributionSource.HYBRID, 100_000_000L)), price)).doesNotThrowAnyException();
        for (ShareRequest bad : List.of(
                new ShareRequest(a, 5000, null, ContributionSource.HYBRID, null),
                new ShareRequest(a, 5000, null, ContributionSource.HYBRID, 100_000_001L))) {
            assertThatThrownBy(() -> valid(List.of(new ShareRequest(o, 5000), bad), price))
                    .isInstanceOf(InvalidSharesException.class);
        }
        assertThatThrownBy(() -> valid(List.of(
                new ShareRequest(o, 5000, null, ContributionSource.POCKET, null), new ShareRequest(a, 5000)), price))
                .isInstanceOf(InvalidSharesException.class);
    }
}
