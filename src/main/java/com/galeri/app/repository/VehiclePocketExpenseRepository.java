package com.galeri.app.repository;

import com.galeri.app.model.entity.VehiclePocketExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehiclePocketExpenseRepository extends JpaRepository<VehiclePocketExpense, UUID> {

    List<VehiclePocketExpense> findByVehicleIdOrderByExpenseDateDescCreatedAtDesc(UUID vehicleId);
}
