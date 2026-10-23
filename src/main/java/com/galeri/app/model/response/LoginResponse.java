package com.galeri.app.model.response;

public record LoginResponse(String token, String fullName, String role, String tenantName) {}
