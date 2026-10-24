package com.galeri.app.repository;

import com.galeri.app.model.entity.MasrafKategori;
import com.galeri.app.model.enums.MasrafKok;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MasrafKategoriRepository extends JpaRepository<MasrafKategori, UUID> {

    // Ayni ad harf buyuklugunden bagimsiz ayni kategori sayilir. Ust null ve dolu icin ayri
    // metot: null UUID parametresi PostgreSQL tip cikarimini bozabiliyor.
    Optional<MasrafKategori> findFirstByTenantIdAndKokAndUstIdIsNullAndAdIgnoreCase(UUID tenantId, MasrafKok kok, String ad);

    Optional<MasrafKategori> findFirstByTenantIdAndKokAndUstIdAndAdIgnoreCase(UUID tenantId, MasrafKok kok, UUID ustId, String ad);

    List<MasrafKategori> findByTenantIdAndKokAndArsivlendiFalse(UUID tenantId, MasrafKok kok);
}
