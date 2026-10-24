package com.galeri.app.repository;

import com.galeri.app.model.entity.Kisi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KisiRepository extends JpaRepository<Kisi, UUID> {

    @Query("""
            select k from Kisi k
            where k.tenantId = :tenantId
              and (:q = '' or lower(k.ad) like lower(concat('%', :q, '%')) or k.telefon like concat('%', :q, '%'))
            order by k.ad
            """)
    // q bos string ise filtre yok (null parametre PostgreSQL tip cikarimini bozar).
    List<Kisi> ara(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
