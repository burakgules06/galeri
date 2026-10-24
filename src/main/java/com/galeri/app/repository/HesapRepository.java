package com.galeri.app.repository;

import com.galeri.app.model.entity.Hesap;
import com.galeri.app.model.enums.HesapTur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HesapRepository extends JpaRepository<Hesap, UUID> {

    Optional<Hesap> findFirstByTenantIdAndTur(UUID tenantId, HesapTur tur);

    Optional<Hesap> findByAracId(UUID aracId);

    List<Hesap> findByTenantIdAndTurInOrderByCreatedAtAsc(UUID tenantId, Collection<HesapTur> turler);
}
