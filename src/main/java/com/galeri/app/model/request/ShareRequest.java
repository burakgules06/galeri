package com.galeri.app.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Ortakli aracta bir ortak: pay baz puan (%50 = 5000). Aracin paylari toplami 10000 olmali.
public record ShareRequest(@NotNull UUID contactId, @NotNull @Min(1) @Max(10000) Integer shareBp) {}
