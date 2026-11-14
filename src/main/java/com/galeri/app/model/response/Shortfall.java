package com.galeri.app.model.response;

import java.util.UUID;

// Parasi yetmeyen kisi ve eksik tutar: hata cevabinin data alaninda doner, panel "Kimden borc alsin?" diye sorar.
public record Shortfall(UUID userId, String name, long shortfallCents) {}
