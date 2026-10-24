package com.galeri.app.model.request;

import com.galeri.app.model.enums.BodyPart;
import com.galeri.app.model.enums.PartCondition;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

// Sadece gonderilen parcalar guncellenir; ORIGINAL gonderilen parcanin kaydi silinir.
public record VehiclePartsRequest(@NotNull Map<BodyPart, PartCondition> parts) {}
