package com.galeri.app.model.request;

import com.galeri.app.model.enums.Parca;
import com.galeri.app.model.enums.ParcaDurum;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

// Sadece gonderilen parcalar guncellenir; ORIJINAL gonderilen parcanin kaydi silinir.
public record ParcalarRequest(@NotNull Map<Parca, ParcaDurum> parcalar) {}
