package com.galeri.app.model.response;

import com.galeri.app.model.enums.ContactType;

import java.util.UUID;

public record ContactResponse(UUID id, String name, String phone, ContactType type, String notes) {}
