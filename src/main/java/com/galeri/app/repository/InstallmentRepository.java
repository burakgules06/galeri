package com.galeri.app.repository;

import com.galeri.app.model.entity.Installment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InstallmentRepository extends JpaRepository<Installment, UUID> {

    // Galerinin tum taksitleri, plan ve kisilerle birlikte (liste ekrani durum / yon / tarih suzmeyi serviste yapar:
    // null tarih parametreleri PostgreSQL tip cikarimini bozuyor).
    @Query("""
            select i from Installment i
            join fetch i.plan p join fetch p.contact join fetch i.payer
            where i.tenantId = :tenantId
            order by i.dueDate, i.seq
            """)
    List<Installment> findAllWithPlan(@Param("tenantId") UUID tenantId);

    @Query("""
            select i from Installment i
            join fetch i.plan p join fetch p.contact join fetch i.payer
            where p.id in :planIds
            order by i.seq
            """)
    List<Installment> findByPlanIds(@Param("planIds") Collection<UUID> planIds);

    boolean existsByPaidEntryId(UUID entryId);

    boolean existsByPlan_IdAndPaidEntryIdIsNotNull(UUID planId);
}
