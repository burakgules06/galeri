package com.galeri.app.repository;

import com.galeri.app.model.entity.PaymentPlanVehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPlanVehicleRepository extends JpaRepository<PaymentPlanVehicle, UUID> {

    List<PaymentPlanVehicle> findByPlanIdOrderByCreatedAtAscIdAsc(UUID planId);

    List<PaymentPlanVehicle> findByPlanIdIn(Collection<UUID> planIds);

    Optional<PaymentPlanVehicle> findByVehicleId(UUID vehicleId);
}
