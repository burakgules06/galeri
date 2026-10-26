package com.galeri.app.repository;

import com.galeri.app.model.entity.CatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, UUID> {

    List<CatalogEntry> findByTenantId(UUID tenantId);
}
