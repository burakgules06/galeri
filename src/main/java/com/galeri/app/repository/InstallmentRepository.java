package com.galeri.app.repository;

import com.galeri.app.model.entity.Installment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentRepository extends JpaRepository<Installment, UUID> {

    /**
     * Taksiti odendi yapar; sadece hala odenmemisse (kosullu guncelleme). Iki es zamanli tahsilattan ikincisi 0 satir gorur
     * (PostgreSQL READ COMMITTED satir kilidi bekleyip kosulu yeniden degerlendirir). Donen deger: guncellenen satir sayisi.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Installment i set i.paidEntryId = :entryId, i.paidAt = :paidAt where i.id = :id and i.paidEntryId is null")
    int markPaid(@Param("id") UUID id, @Param("entryId") UUID entryId, @Param("paidAt") OffsetDateTime paidAt);

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

    // Odeme gunu bildirimi: butun galerilerin odenmemis, vadesi [from, to] araliginda taksitleri (zamanlanmis gorev,
    // oturum yok; galeri ayrimi serviste yapilir)
    @Query("""
            select i from Installment i
            join fetch i.plan p join fetch p.contact join fetch i.payer
            where i.paidEntryId is null and i.dueDate >= :from and i.dueDate <= :to
            order by i.tenantId, i.dueDate, i.seq
            """)
    List<Installment> findOpenDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByPaidEntryId(UUID entryId);

    boolean existsByCapitalEntryId(UUID entryId);

    Optional<Installment> findByPaidEntryId(UUID entryId);

    Optional<Installment> findByEndorseEntryId(UUID entryId);

    /** Bolme, ciro ve karsiliksiz isaretleme ayni taksitte es zamanli calismasin diye satir kilidi. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Installment i where i.id = :id")
    Optional<Installment> lockById(@Param("id") UUID id);

    /** Kismi tahsilat / ciro icin taksitin tutarini kucultur; sadece odenmemisse. Kalan ayri taksit olarak eklenir. */
    @Modifying(flushAutomatically = true)
    @Query(value = "update installment set amount_cents = :amount, updated_at = now() where id = :id and paid_entry_id is null",
            nativeQuery = true)
    int resize(@Param("id") UUID id, @Param("amount") long amountCents);

    @Query("select coalesce(max(i.seq), 0) from Installment i where i.plan.id = :planId")
    int maxSeq(@Param("planId") UUID planId);

    boolean existsByPlan_IdAndPaidEntryIdIsNotNull(UUID planId);

    /** Sermayedara ait (V18) henuz tahsil edilmemis evrak toplami, kisi basina: [holderContactId, toplam kurus]. */
    @Query("""
            select p.holderContactId, sum(i.amountCents) from Installment i join i.plan p
            where p.tenantId = :tenantId and p.holderContactId is not null and i.paidEntryId is null
            group by p.holderContactId
            """)
    List<Object[]> pendingByHolder(@Param("tenantId") UUID tenantId);
}
