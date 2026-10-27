package com.galeri.app.service;

import com.galeri.app.model.response.CapitalResponses;
import java.util.List;
import com.galeri.app.model.response.NetWorthResponse;

public interface NetWorthService {
    NetWorthResponse summary();

    /** Sermayedarlar ve dukkan sahibi: kimin ne kadar sermayesi var, ne kadari nakitte, hangi aracta ne kadar. */
    List<CapitalResponses.Holder> holders();
}
