package com.galeri.app.repository;

import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @Query("""
            select v from Vehicle v
            left join fetch v.buyer b
            left join v.seller s
            where v.tenantId = :tenantId
              and ((:status is null and v.status <> com.galeri.app.model.enums.VehicleStatus.REMOVED) or v.status = :status)
              and (:q = ''
                   or lower(v.plate) like lower(concat('%', :q, '%'))
                   or lower(v.make) like lower(concat('%', :q, '%'))
                   or lower(v.model) like lower(concat('%', :q, '%'))
                   or (:withContacts = true and (lower(b.name) like lower(concat('%', :q, '%'))
                                                 or lower(s.name) like lower(concat('%', :q, '%'))))
                   or (:phone <> '' and (b.phone like concat('%', :phone, '%') or s.phone like concat('%', :phone, '%'))))
            order by v.purchaseDate desc, v.createdAt desc
            """)
    // q bos string ise filtre yok; status null ise tum durumlar. withContacts / phone sadece ADMIN icin dolu:
    // alici ve satici adi ya da telefonuyla arar (STAFF kisilerin bilgisini goremez).
    List<Vehicle> search(@Param("tenantId") UUID tenantId, @Param("status") VehicleStatus status, @Param("q") String q,
                         @Param("withContacts") boolean withContacts, @Param("phone") String phone);

    boolean existsByTenantIdAndPlateAndStatus(UUID tenantId, String plate, VehicleStatus status);

    List<Vehicle> findByTenantIdAndStatus(UUID tenantId, VehicleStatus status);

    // Kaldirilan (yanlis girilen) araclar alis raporlarina girmez
    @Query("""
            select v from Vehicle v where v.tenantId = :tenantId and v.purchaseDate between :start and :end
              and v.status <> com.galeri.app.model.enums.VehicleStatus.REMOVED
            """)
    List<Vehicle> findByTenantIdAndPurchaseDateBetween(@Param("tenantId") UUID tenantId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Vehicle> findByTenantIdAndSaleDateBetween(UUID tenantId, LocalDate start, LocalDate end);

    @Query("""
            select v from Vehicle v where v.tenantId = :tenantId and v.ownership = :ownership
              and v.status <> com.galeri.app.model.enums.VehicleStatus.REMOVED
            """)
    List<Vehicle> findByTenantIdAndOwnership(@Param("tenantId") UUID tenantId, @Param("ownership") Ownership ownership);
}
