package com.galeri.app.repository;

import com.galeri.app.model.entity.PaymentPlan;
import com.galeri.app.model.enums.PlanDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {

    Optional<PaymentPlan> findByVehicleIdAndDirection(UUID vehicleId, PlanDirection direction);

    List<PaymentPlan> findByVehicleIdOrderByDirection(UUID vehicleId);
}
