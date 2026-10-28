package com.galeri.app.repository;

import com.galeri.app.model.entity.PaymentPlan;
import com.galeri.app.model.enums.PlanDirection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentPlan p where p.id = :id")
    Optional<PaymentPlan> lockById(@Param("id") UUID id);

    @Modifying
    @Query(value = "update payment_plan set total_cents = :total, opening_entry_id = :entryId, updated_at = now() where id = :id", nativeQuery = true)
    int replaceOpenItemsAmountAndOpeningEntry(@Param("id") UUID id, @Param("total") long totalCents, @Param("entryId") UUID entryId);

    Optional<PaymentPlan> findByVehicleIdAndDirection(UUID vehicleId, PlanDirection direction);

    List<PaymentPlan> findByVehicleIdOrderByDirection(UUID vehicleId);

    boolean existsByOpeningEntryId(UUID entryId);
}
