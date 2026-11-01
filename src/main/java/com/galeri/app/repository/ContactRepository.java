package com.galeri.app.repository;

import com.galeri.app.model.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    @Query("""
            select c from Contact c
            where c.tenantId = :tenantId
              and (:q = '' or lower(c.name) like lower(concat('%', :q, '%')) or c.phone like concat('%', :q, '%'))
            order by c.name
            """)
    // q bos string ise filtre yok (null parametre PostgreSQL tip cikarimini bozar).
    List<Contact> search(@Param("tenantId") UUID tenantId, @Param("q") String q);

    /** Bizden arac almis kisiler (etiket "Araç sattık"). */
    @Query("select distinct v.buyer.id from Vehicle v where v.tenantId = :tenantId and v.buyer is not null")
    List<UUID> buyerIds(@Param("tenantId") UUID tenantId);

    /** Bize arac satmis kisiler (etiket "Araç aldık"). */
    @Query("select distinct v.seller.id from Vehicle v where v.tenantId = :tenantId and v.seller is not null")
    List<UUID> sellerIds(@Param("tenantId") UUID tenantId);

    /** Dukkan sahibi (galeri basina en fazla bir, uq_contact_owner). */
}
