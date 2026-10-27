package com.galeri.app.repository;

import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.LedgerEntryType;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // Hesap bazinda toplamlar: [accountId, totalCents]
    @Query("select e.targetAccount.id, sum(e.amountCents) from LedgerEntry e where e.tenantId = :tenantId group by e.targetAccount.id")
    List<Object[]> incomingTotals(@Param("tenantId") UUID tenantId);

    @Query("select e.sourceAccount.id, sum(e.amountCents) from LedgerEntry e where e.tenantId = :tenantId group by e.sourceAccount.id")
    List<Object[]> outgoingTotals(@Param("tenantId") UUID tenantId);

    /** Bu turdeki hesaplara (ornegin tum CAPITAL hesaplari) giren ya da cikan hareketler; tarih filtresi cagirida. */
    @Query("""
            select e from LedgerEntry e
            join fetch e.sourceAccount join fetch e.targetAccount
            where e.tenantId = :tenantId and (e.sourceAccount.type = :type or e.targetAccount.type = :type)
            """)
    List<LedgerEntry> touchingType(@Param("tenantId") UUID tenantId, @Param("type") AccountType type);

    /** Tarih araliginda (iki uc dahil) bu turdeki hesaplara net giris: giren - cikan. Ornek: donemin genel giderleri. */
    @Query("""
            select coalesce(sum(case when e.targetAccount.type = :type then e.amountCents else -e.amountCents end), 0)
            from LedgerEntry e
            where e.tenantId = :tenantId and e.entryDate between :from and :to
              and (e.targetAccount.type = :type or e.sourceAccount.type = :type)
            """)
    long netInto(@Param("tenantId") UUID tenantId, @Param("type") AccountType type,
                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Bir hesaba dokunan bu turdeki hareketler (ornegin acilis bakiyesi bir kez mi girildi). */
    @Query("select e from LedgerEntry e where e.type = :type and (e.sourceAccount.id = :accountId or e.targetAccount.id = :accountId)")
    List<LedgerEntry> byTypeTouching(@Param("type") LedgerEntryType type, @Param("accountId") UUID accountId);

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

    /**
     * Hareketi satir kilidiyle okur (SELECT ... FOR UPDATE): ayni hareket iki istekte birden terslenmesin.
     * Kilit satiri degistirmez; hareket tablosunun UPDATE/DELETE yasagi (trigger) etkilenmez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LedgerEntry e where e.id = :id")
    Optional<LedgerEntry> lockById(@Param("id") UUID id);

    @Query("select e.correctedEntryId from LedgerEntry e where e.correctedEntryId in :ids")
    List<UUID> correctedIds(@Param("ids") Collection<UUID> ids);

    List<LedgerEntry> findByVehicle_IdAndType(UUID vehicleId, LedgerEntryType type);

    List<LedgerEntry> findByVehicle_Id(UUID vehicleId);

    // Arac bazinda maliyete giren masraf toplami (duzeltmeler ters yonde oldugu icin kendiliginden duser): [vehicleId, totalCents].
    // Sadece aracin maliyet hesabina (VEHICLE) dokunanlar: konsinye aracin genel gidere ya da sahibine yazilan masraflari sayilmaz.
    @Query("""
            select e.vehicle.id, sum(case when e.targetAccount.type = :vehicleType then e.amountCents else -e.amountCents end)
            from LedgerEntry e
            where e.tenantId = :tenantId and e.type = :type and e.vehicle is not null
              and (e.targetAccount.type = :vehicleType or e.sourceAccount.type = :vehicleType)
            group by e.vehicle.id
            """)
    List<Object[]> vehicleExpenseTotals(@Param("tenantId") UUID tenantId, @Param("type") LedgerEntryType type,
                                        @Param("vehicleType") AccountType vehicleType);

    // Ortak araclarda ortaklara yazilan kar payi, arac bazinda net (CAPITAL'den INCOME'a arti; zarar ve duzeltmeler eksi):
    // [vehicleId, totalCents]. Havuzun payi = aracin kari - bu tutar.
    @Query("""
            select e.vehicle.id, sum(case when e.sourceAccount.type = :capitalType then e.amountCents else -e.amountCents end)
            from LedgerEntry e
            where e.tenantId = :tenantId and e.type = :type and e.vehicle is not null
            group by e.vehicle.id
            """)
    List<Object[]> partnerProfitShareTotals(@Param("tenantId") UUID tenantId, @Param("type") LedgerEntryType type,
                                           @Param("capitalType") AccountType capitalType);

    // Kategori kullanim sayisi (hizli butonlar icin), duzeltmeler sayilmaz: [categoryId, count]
    @Query("""
            select e.category.id, count(e) from LedgerEntry e
            where e.tenantId = :tenantId and e.category is not null and e.correctedEntryId is null
            group by e.category.id
            """)
    List<Object[]> categoryUsage(@Param("tenantId") UUID tenantId);
}
