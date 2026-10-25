package com.galeri.app.repository;

import com.galeri.app.model.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    // Bu gun icin bildirimi zaten gitmis taksitler
    @Query("select l.installmentId from NotificationLog l where l.notifyDate = :date and l.installmentId in :ids")
    List<UUID> notifiedOn(@Param("date") LocalDate date, @Param("ids") Collection<UUID> ids);
}
