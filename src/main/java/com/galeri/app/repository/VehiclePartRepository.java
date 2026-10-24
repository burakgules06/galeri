package com.galeri.app.repository;

import com.galeri.app.model.entity.VehiclePart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VehiclePartRepository extends JpaRepository<VehiclePart, UUID> {

    List<VehiclePart> findByVehicleId(UUID vehicleId);

    List<VehiclePart> findByVehicleIdIn(Collection<UUID> vehicleIds);

    void deleteByVehicleId(UUID vehicleId);
}
