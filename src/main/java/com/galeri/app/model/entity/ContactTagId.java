package com.galeri.app.model.entity;

import com.galeri.app.model.enums.ContactTag;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ContactTagId implements Serializable {

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "tag", nullable = false, length = 32)
    private String tag;

    public ContactTagId(UUID contactId, ContactTag tag) {
        this.contactId = contactId;
        this.tag = tag.name();
    }
}
