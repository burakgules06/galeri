package com.galeri.app.repository;

import com.galeri.app.model.entity.ContactTagEntity;
import com.galeri.app.model.entity.ContactTagId;
import com.galeri.app.model.enums.ContactTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContactTagRepository extends JpaRepository<ContactTagEntity, ContactTagId> {

    List<ContactTagEntity> findByTenantIdAndContact_IdIn(UUID tenantId, Collection<UUID> contactIds);

    @Modifying
    @Query(value = """
            INSERT INTO contact_tag (tenant_id, contact_id, tag)
            VALUES (:tenantId, :contactId, :tag)
            ON CONFLICT (contact_id, tag) DO NOTHING
            """, nativeQuery = true)
    int addIfMissing(@Param("tenantId") UUID tenantId, @Param("contactId") UUID contactId, @Param("tag") String tag);

    void deleteByTenantIdAndContact_IdAndTag(UUID tenantId, UUID contactId, ContactTag tag);
}
