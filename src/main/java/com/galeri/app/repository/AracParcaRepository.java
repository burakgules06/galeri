package com.galeri.app.repository;

import com.galeri.app.model.entity.AracParca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AracParcaRepository extends JpaRepository<AracParca, UUID> {

    List<AracParca> findByAracId(UUID aracId);

    List<AracParca> findByAracIdIn(Collection<UUID> aracIds);

    void deleteByAracId(UUID aracId);
}
