package com.galeri.app.repository;

import com.galeri.app.model.entity.VehicleShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VehicleShareRepository extends JpaRepository<VehicleShare, UUID> {

    @Query("select s from VehicleShare s join fetch s.contact where s.vehicleId = :vehicleId order by s.shareBp desc")
    List<VehicleShare> findByVehicleIdWithContact(@Param("vehicleId") UUID vehicleId);

    List<VehicleShare> findByVehicleId(UUID vehicleId);
}
