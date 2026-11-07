package com.galeri.app.model.response;

// canTrade / canAddExpense: calisan pesin arac alim / satimi, arac masrafi girebilir mi (panel butonlari icin;
// yetki backend'de her istekte kontrol edilir)
public record LoginResponse(String token, String fullName, String role, String tenantName, boolean canTrade, boolean canAddExpense) {}
