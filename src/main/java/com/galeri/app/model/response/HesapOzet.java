package com.galeri.app.model.response;

import com.galeri.app.model.enums.HesapTur;

import java.util.UUID;

public record HesapOzet(UUID id, HesapTur tur, String ad) {}
