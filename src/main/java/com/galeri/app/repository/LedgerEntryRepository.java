package com.galeri.app.repository;

import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.enums.LedgerEntryType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // Hesap bazinda toplamlar: [accountId, totalCents]
    @Query("select e.targetAccount.id, sum(e.amountCents) from LedgerEntry e where e.tenantId = :tenantId group by e.targetAccount.id")
    List<Object[]> incomingTotals(@Param("tenantId") UUID tenantId);

    @Query("select e.sourceAccount.id, sum(e.amountCents) from LedgerEntry e where e.tenantId = :tenantId group by e.sourceAccount.id")
    List<Object[]> outgoingTotals(@Param("tenantId") UUID tenantId);

    @Query("""
            select e from LedgerEntry e
            join fetch e.sourceAccount join fetch e.targetAccount join fetch e.createdBy
            left join fetch e.vehicle left join fetch e.category
            where e.tenantId = :tenantId
            order by e.entryDate desc, e.createdAt desc
            """)
    List<LedgerEntry> list(@Param("tenantId") UUID tenantId, Pageable page);

    @Query("""
            select e from LedgerEntry e
            join fetch e.sourceAccount join fetch e.targetAccount join fetch e.createdBy
            left join fetch e.vehicle left join fetch e.category
            where e.tenantId = :tenantId and (e.sourceAccount.id = :accountId or e.targetAccount.id = :accountId)
            order by e.entryDate desc, e.createdAt desc
            """)
    List<LedgerEntry> accountEntries(@Param("tenantId") UUID tenantId, @Param("accountId") UUID accountId, Pageable page);

    @Query("""
            select e from LedgerEntry e
            join fetch e.sourceAccount join fetch e.targetAccount join fetch e.createdBy
            left join fetch e.category
            where e.vehicle.id = :vehicleId
            order by e.entryDate, e.createdAt
            """)
    List<LedgerEntry> vehicleEntries(@Param("vehicleId") UUID vehicleId);

    boolean existsByCorrectedEntryId(UUID entryId);

    @Query("select e.correctedEntryId from LedgerEntry e where e.correctedEntryId in :ids")
    List<UUID> correctedIds(@Param("ids") Collection<UUID> ids);

    List<LedgerEntry> findByVehicle_IdAndType(UUID vehicleId, LedgerEntryType type);

    // Kategori kullanim sayisi (hizli butonlar icin), duzeltmeler sayilmaz: [categoryId, count]
    @Query("""
            select e.category.id, count(e) from LedgerEntry e
            where e.tenantId = :tenantId and e.category is not null and e.correctedEntryId is null
            group by e.category.id
            """)
    List<Object[]> categoryUsage(@Param("tenantId") UUID tenantId);
}
