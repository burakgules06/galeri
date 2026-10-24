package com.galeri.app.repository;

import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.enums.AracDurum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AracRepository extends JpaRepository<Arac, UUID> {

    @Query("""
            select a from Arac a
            where a.tenantId = :tenantId
              and (:durum is null or a.durum = :durum)
              and (:q = ''
                   or lower(a.plaka) like lower(concat('%', :q, '%'))
                   or lower(a.marka) like lower(concat('%', :q, '%'))
                   or lower(a.model) like lower(concat('%', :q, '%')))
            order by a.alisTarihi desc, a.createdAt desc
            """)
    // q bos string ise filtre yok; durum null ise tum durumlar.
    List<Arac> ara(@Param("tenantId") UUID tenantId, @Param("durum") AracDurum durum, @Param("q") String q);

    boolean existsByTenantIdAndPlakaAndDurum(UUID tenantId, String plaka, AracDurum durum);

    List<Arac> findByTenantIdAndAlisTarihiBetween(UUID tenantId, LocalDate baslangic, LocalDate bitis);

    List<Arac> findByTenantIdAndSatisTarihiBetween(UUID tenantId, LocalDate baslangic, LocalDate bitis);
}
