package com.galeri.app.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Yeni banka hesabi. Kasa her galeride tektir ve kendiliginden olusur.
public record AccountRequest(@NotBlank @Size(max = 120) String name) {}
