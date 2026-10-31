package com.galeri.app.repository;

import com.galeri.app.model.entity.VehicleShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VehicleShareRepository extends JpaRepository<VehicleShare, UUID> {

    // Siralama sabit: buyuk pay once, esitlikte eklenme sirasi
    @Query("select s from VehicleShare s join fetch s.user u left join fetch u.tenant where s.vehicleId = :vehicleId order by s.shareBp desc, s.createdAt, s.id")
    List<VehicleShare> findByVehicleIdWithUser(@Param("vehicleId") UUID vehicleId);

    List<VehicleShare> findByVehicleId(UUID vehicleId);

    boolean existsByUser_Id(UUID userId);
}
