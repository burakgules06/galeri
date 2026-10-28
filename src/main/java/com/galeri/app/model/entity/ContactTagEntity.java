package com.galeri.app.model.entity;

import com.galeri.app.model.enums.ContactTag;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "contact_tag")
@Getter
@Setter
public class ContactTagEntity extends BaseModel {

    @EmbeddedId
    private ContactTagId id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @MapsId("contactId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, insertable = false, updatable = false)
    private ContactTag tag;
}
