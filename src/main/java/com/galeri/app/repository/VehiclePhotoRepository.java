package com.galeri.app.repository;

import com.galeri.app.model.entity.VehiclePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VehiclePhotoRepository extends JpaRepository<VehiclePhoto, UUID> {

    List<VehiclePhoto> findByVehicleIdOrderByPositionAsc(UUID vehicleId);

    List<VehiclePhoto> findByVehicleIdInOrderByPositionAsc(Collection<UUID> vehicleIds);

    int countByVehicleId(UUID vehicleId);
}
