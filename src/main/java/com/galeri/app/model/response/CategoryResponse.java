package com.galeri.app.model.response;

import com.galeri.app.model.enums.ExpenseRoot;

import java.util.UUID;

// usage: bu kategoriyle girilmis masraf sayisi (hizli butonlar buna gore siralanir).
public record CategoryResponse(UUID id, String name, ExpenseRoot root, UUID parentId, long usage) {}
