package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.enums.ContactKind;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.response.ContactResponse;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceImplTest {

    @Mock private ContactRepository contactRepository;
    private ContactServiceImpl service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TestSession.open(UUID.randomUUID(), tenantId, "ADMIN");
        service = new ContactServiceImpl(contactRepository);
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void yeniKisi_turVerilmezseMusteri_verilirseOTur() {
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.create(new ContactRequest("ali yılmaz", null, ContactType.PERSON, null)).kind()).isEqualTo(ContactKind.CUSTOMER);
        ContactResponse usta = service.create(new ContactRequest("Boya Usta", null, ContactType.ORGANIZATION, null, ContactKind.SUPPLIER));
        assertThat(usta.kind()).isEqualTo(ContactKind.SUPPLIER);
        assertThat(usta.type()).isEqualTo(ContactType.ORGANIZATION);
    }

    @Test
    void duzenlemedeTurGelmezse_anaTurKorunur() {
        Contact c = contact("Mehmet", ContactKind.SELLER);
        when(contactRepository.findById(c.getId())).thenReturn(Optional.of(c));
        assertThat(service.update(c.getId(), new ContactRequest("Mehmet", null, ContactType.PERSON, null)).kind()).isEqualTo(ContactKind.SELLER);
    }

    @Test
    void liste_islemlerdenEtiketGelir_aracSattikVeAldik() {
        Contact both = contact("Ali", ContactKind.CUSTOMER), none = contact("Veli", ContactKind.CUSTOMER);
        when(contactRepository.search(tenantId, "")).thenReturn(List.of(both, none));
        when(contactRepository.buyerIds(tenantId)).thenReturn(List.of(both.getId()));
        when(contactRepository.sellerIds(tenantId)).thenReturn(List.of(both.getId()));
        List<ContactResponse> list = service.search(null);
        assertThat(list.get(0).tags()).containsExactly("SOLD_TO", "BOUGHT_FROM");
        assertThat(list.get(1).tags()).isEmpty();
    }

    private Contact contact(String name, ContactKind kind) {
        Contact c = new Contact();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setName(name);
        c.setKind(kind);
        return c;
    }
}
