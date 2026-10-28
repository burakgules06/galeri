package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.ContactTagEntity;
import com.galeri.app.model.enums.ContactTag;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.response.ContactResponse;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.ContactTagRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.ContactService;
import com.galeri.app.util.PhoneUtil;
import com.galeri.app.util.NameUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final ContactTagRepository contactTagRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContactResponse> search(String q) {
        String query = q == null ? "" : q.trim();
        List<Contact> contacts = contactRepository.search(CurrentUser.tenantId(), query);
        if (contacts.isEmpty()) return List.of();
        Map<UUID, Set<ContactTag>> tags = tagsByContact(contacts);
        return contacts.stream().map(c -> toResponse(c, tags.getOrDefault(c.getId(), Set.of()))).toList();
    }

    @Override
    @Transactional
    public ContactResponse create(ContactRequest request) {
        Contact contact = new Contact();
        contact.setTenantId(CurrentUser.tenantId());
        apply(contact, request);
        return toResponse(contactRepository.save(contact), Set.of());
    }

    @Override
    @Transactional
    public ContactResponse update(UUID id, ContactRequest request) {
        Contact contact = contactRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!contact.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        apply(contact, request);
        return toResponse(contact, tagsByContact(List.of(contact)).getOrDefault(contact.getId(), Set.of()));
    }

    private static void apply(Contact contact, ContactRequest request) {
        contact.setName(NameUtil.normalize(request.name()));
        contact.setPhone(PhoneUtil.normalize(request.phone()));
        contact.setType(request.type() != null ? request.type() : ContactType.PERSON);
        contact.setNotes(request.notes());
    }

    private Map<UUID, Set<ContactTag>> tagsByContact(List<Contact> contacts) {
        return contactTagRepository.findByTenantIdAndContact_IdIn(CurrentUser.tenantId(), contacts.stream().map(Contact::getId).toList())
                .stream().collect(Collectors.groupingBy(t -> t.getContact().getId(),
                        Collectors.mapping(ContactTagEntity::getTag, Collectors.toSet())));
    }

    static ContactResponse toResponse(Contact c, Set<ContactTag> tags) {
        return new ContactResponse(c.getId(), c.getName(), c.getPhone(), c.getType(), c.getNotes(),
                tags.stream().sorted().toList());
    }

}
