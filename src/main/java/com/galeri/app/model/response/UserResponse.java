package com.galeri.app.model.response;

import java.util.UUID;

// Kullanicilar ekrani. self: istegi yapan kullanicinin kendisi (panel kendini pasiflestirme dugmesini gizler).
public record UserResponse(UUID id, String fullName, String phone, String role, boolean active, boolean self) {}
