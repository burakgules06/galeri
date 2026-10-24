package com.galeri.app.model.response;

import com.galeri.app.model.enums.HesapTur;

import java.util.UUID;

// Bakiye hareketlerden hesaplanir (giren - cikan), kurus.
public record HesapResponse(UUID id, HesapTur tur, String ad, long bakiyeKurus) {}
