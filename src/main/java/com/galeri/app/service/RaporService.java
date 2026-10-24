package com.galeri.app.service;

import com.galeri.app.model.response.AylikAlimSatim;

public interface RaporService {
    /** ADMIN tum calisanlari, STAFF sadece kendini gorur. */
    AylikAlimSatim aylikAlimSatim(int yil, int ay);
}
