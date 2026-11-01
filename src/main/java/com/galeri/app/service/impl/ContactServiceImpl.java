package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.response.ContactResponse;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.ContactService;
import com.galeri.app.util.PhoneUtil;
import com.galeri.app.util.NameUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContactResponse> search(String q) {
        String query = q == null ? "" : q.trim();
        UUID tenantId = CurrentUser.tenantId();
        java.util.Set<UUID> soldTo = new java.util.HashSet<>(contactRepository.buyerIds(tenantId));
        java.util.Set<UUID> boughtFrom = new java.util.HashSet<>(contactRepository.sellerIds(tenantId));
        return contactRepository.search(tenantId, query).stream().map(c -> {
            List<String> tags = new java.util.ArrayList<>();
            if (soldTo.contains(c.getId())) tags.add("SOLD_TO");
            if (boughtFrom.contains(c.getId())) tags.add("BOUGHT_FROM");
            return toResponse(c, tags);
        }).toList();
    }

    @Override
    @Transactional
    public ContactResponse create(ContactRequest request) {
        Contact contact = new Contact();
        contact.setTenantId(CurrentUser.tenantId());
        apply(contact, request);
        return toResponse(contactRepository.save(contact));
    }

    @Override
    @Transactional
    public ContactResponse update(UUID id, ContactRequest request) {
        Contact contact = contactRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!contact.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        apply(contact, request);
        return toResponse(contact);
    }

    private static void apply(Contact contact, ContactRequest request) {
        contact.setName(NameUtil.normalize(request.name()));
        contact.setPhone(PhoneUtil.normalize(request.phone()));
        contact.setType(request.type() != null ? request.type() : ContactType.PERSON);
        contact.setNotes(request.notes());
        if (request.kind() != null) contact.setKind(request.kind());   // duzenlemede bos gelirse ana tur korunur
    }

    static ContactResponse toResponse(Contact c) {
        return toResponse(c, List.of());
    }

    static ContactResponse toResponse(Contact c, List<String> tags) {
        return new ContactResponse(c.getId(), c.getName(), c.getPhone(), c.getType(), c.getNotes(), c.getKind(), tags);
    }
}
