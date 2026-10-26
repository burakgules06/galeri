package com.galeri.app.repository;

import com.galeri.app.model.entity.VehicleShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VehicleShareRepository extends JpaRepository<VehicleShare, UUID> {

    // Havuz satirinda kisi yok (left join); siralama sabit: buyuk pay once, esitlikte eklenme sirasi
    @Query("select s from VehicleShare s left join fetch s.contact where s.vehicleId = :vehicleId order by s.shareBp desc, s.createdAt, s.id")
    List<VehicleShare> findByVehicleIdWithContact(@Param("vehicleId") UUID vehicleId);

    List<VehicleShare> findByVehicleId(UUID vehicleId);
}
