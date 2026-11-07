package com.galeri.app.model.response;

// canTrade: calisan pesin arac alim / satimi girebilir mi (panel butonlari icin; yetki backend'de her istekte kontrol edilir)
public record LoginResponse(String token, String fullName, String role, String tenantName, boolean canTrade) {}
