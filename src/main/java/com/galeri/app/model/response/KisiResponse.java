package com.galeri.app.model.response;

import com.galeri.app.model.enums.KisiTur;

import java.util.UUID;

public record KisiResponse(UUID id, String ad, String telefon, KisiTur tur, String notlar) {}
