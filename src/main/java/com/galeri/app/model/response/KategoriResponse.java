package com.galeri.app.model.response;

import com.galeri.app.model.enums.MasrafKok;

import java.util.UUID;

// kullanim: bu kategoriyle girilmis masraf sayisi (hizli butonlar buna gore siralanir).
public record KategoriResponse(UUID id, String ad, MasrafKok kok, UUID ustId, long kullanim) {}
