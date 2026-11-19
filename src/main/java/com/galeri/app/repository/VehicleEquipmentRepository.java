package com.galeri.app.repository;

import com.galeri.app.model.entity.VehicleEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VehicleEquipmentRepository extends JpaRepository<VehicleEquipment, UUID> {

    List<VehicleEquipment> findByVehicleIdOrderByPositionAsc(UUID vehicleId);

    /** Liste bastan yazilirken eskiler hemen silinir (yenileri ayni islemde eklenir). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from VehicleEquipment e where e.vehicleId = :vehicleId")
    void deleteAllOfVehicle(UUID vehicleId);
}
