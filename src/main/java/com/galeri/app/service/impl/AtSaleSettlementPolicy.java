package com.galeri.app.service.impl;

import com.galeri.app.service.CoOwnerSettlementPolicy;
import org.springframework.stereotype.Component;

// Karar (revizyon PR 3): ortagin alacagi satis aninda hesaplanir ve satisla ayni transaction'da yazilir.
@Component
public class AtSaleSettlementPolicy implements CoOwnerSettlementPolicy {

    @Override
    public Timing timing() {
        return Timing.AT_SALE;
    }
}
