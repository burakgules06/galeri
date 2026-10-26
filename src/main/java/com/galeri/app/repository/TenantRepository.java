package com.galeri.app.repository;

import com.galeri.app.model.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.reminderTime <= :time and (t.reminderSentOn is null or t.reminderSentOn <> :today) order by t.id")
    List<Tenant> findDueReminders(LocalDate today, LocalTime time);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> lockById(UUID id);
}
