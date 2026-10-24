package com.galeri.app.service;

import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.response.ContactResponse;

import java.util.List;
import java.util.UUID;

public interface ContactService {
    List<ContactResponse> search(String q);

    ContactResponse create(ContactRequest request);

    ContactResponse update(UUID id, ContactRequest request);
}
