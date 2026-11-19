package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Aracin donanim listesi (tamami): sirasiyla. Bos liste = donanim yok. Ayni ad iki kez yazilmaz (buyuk / kucuk harf fark etmez).
public record VehicleEquipmentRequest(@NotNull @Size(max = 80) List<@NotNull @Size(max = 80) String> items) {}
