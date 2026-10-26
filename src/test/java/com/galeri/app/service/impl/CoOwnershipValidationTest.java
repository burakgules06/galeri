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

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void havuzVeOrtak_yuzde50_gecerli_karPayiOraniFarkliOlabilir() {
        assertThatCode(() -> CoOwnershipServiceImpl.validate(List.of(new ShareRequest(null, 5000), new ShareRequest(a, 5000)), 200_000_000L))
                .doesNotThrowAnyException();
        assertThatCode(() -> CoOwnershipServiceImpl.validate(List.of(
                new ShareRequest(null, 5000, 4000, null, null), new ShareRequest(a, 5000, 6000, null, null)), 200_000_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void paylarYaDaKarPayiOranlari100DegilseYaDaTekrarliysa_reddedilir() {
        List<List<ShareRequest>> wrong = List.of(
                List.of(new ShareRequest(a, 5000), new ShareRequest(b, 4000)),                                    // eksik
                List.of(new ShareRequest(a, 6000), new ShareRequest(b, 5000)),                                    // fazla
                List.of(new ShareRequest(a, 5000), new ShareRequest(a, 5000)),                                    // kisi tekrar
                List.of(new ShareRequest(null, 5000), new ShareRequest(null, 5000)),                              // iki havuz, ortak yok
                List.of(new ShareRequest(null, 10000)),                                                           // sadece havuz
                List.of(new ShareRequest(a, 5000, 7000, null, null), new ShareRequest(b, 5000, 5000, null, null)), // kar payi %120
                List.of());
        for (List<ShareRequest> owners : wrong) {
            assertThatThrownBy(() -> CoOwnershipServiceImpl.validate(owners, 100_000_000L)).isInstanceOf(InvalidSharesException.class);
        }
    }

    @Test
    void hibrit_ceptenTutarZorunlu_katkiyiAsamaz_havuzCeptenOlamaz() {
        long price = 200_000_000L;   // katki 1.000.000
        assertThatCode(() -> CoOwnershipServiceImpl.validate(List.of(new ShareRequest(null, 5000),
                new ShareRequest(a, 5000, null, ContributionSource.HYBRID, 100_000_000L)), price)).doesNotThrowAnyException();
        for (ShareRequest bad : List.of(
                new ShareRequest(a, 5000, null, ContributionSource.HYBRID, null),
                new ShareRequest(a, 5000, null, ContributionSource.HYBRID, 100_000_001L))) {
            assertThatThrownBy(() -> CoOwnershipServiceImpl.validate(List.of(new ShareRequest(null, 5000), bad), price))
                    .isInstanceOf(InvalidSharesException.class);
        }
        assertThatThrownBy(() -> CoOwnershipServiceImpl.validate(List.of(
                new ShareRequest(null, 5000, null, ContributionSource.POCKET, null), new ShareRequest(a, 5000)), price))
                .isInstanceOf(InvalidSharesException.class);
    }
}
