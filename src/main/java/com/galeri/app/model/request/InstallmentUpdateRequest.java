package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Odenmemis taksitte sadece vade ve senet no degisir; tutar ve odeyen degismez.
public record InstallmentUpdateRequest(@NotNull LocalDate dueDate, @Size(max = 40) String noteNo) {}
