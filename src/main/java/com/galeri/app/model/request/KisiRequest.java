package com.galeri.app.model.request;

import com.galeri.app.model.enums.KisiTur;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KisiRequest(
        @NotBlank @Size(max = 120) String ad,
        @Size(max = 20) String telefon,
        KisiTur tur,
        String notlar
) {}
