package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Odenmemis taksitte sadece vade, senet / cek no ve cek bilgisi degisir; tutar ve odeyen degismez.
public record InstallmentUpdateRequest(
        @NotNull LocalDate dueDate,
        @Size(max = 40) String noteNo,
        @Size(max = 60) String bankName,
        @Size(max = 120) String drawerName,
        @Size(max = 500) String description
) {
    public InstallmentUpdateRequest(LocalDate dueDate, String noteNo) {
        this(dueDate, noteNo, null, null, null);
    }

    public InstallmentUpdateRequest(LocalDate dueDate, String noteNo, String bankName, String drawerName) {
        this(dueDate, noteNo, bankName, drawerName, null);
    }
}
