package com.galeri.app.repository;

import com.galeri.app.model.entity.Vehicle;
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
            where v.tenantId = :tenantId
              and (:status is null or v.status = :status)
              and (:q = ''
                   or lower(v.plate) like lower(concat('%', :q, '%'))
                   or lower(v.make) like lower(concat('%', :q, '%'))
                   or lower(v.model) like lower(concat('%', :q, '%')))
            order by v.purchaseDate desc, v.createdAt desc
            """)
    // q bos string ise filtre yok; status null ise tum durumlar.
    List<Vehicle> search(@Param("tenantId") UUID tenantId, @Param("status") VehicleStatus status, @Param("q") String q);

    boolean existsByTenantIdAndPlateAndStatus(UUID tenantId, String plate, VehicleStatus status);

    List<Vehicle> findByTenantIdAndPurchaseDateBetween(UUID tenantId, LocalDate start, LocalDate end);

    List<Vehicle> findByTenantIdAndSaleDateBetween(UUID tenantId, LocalDate start, LocalDate end);
}
