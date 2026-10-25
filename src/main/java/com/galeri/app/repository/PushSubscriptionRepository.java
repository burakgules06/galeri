package com.galeri.app.repository;

import com.galeri.app.model.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    // Galerinin tum abonelikleri, kullanicisiyla (rol / aktiflik kontrolu serviste)
    @Query("select s from PushSubscription s join fetch s.panelUser where s.tenantId = :tenantId")
    List<PushSubscription> findByTenantIdWithUser(@Param("tenantId") UUID tenantId);

    void deleteByEndpoint(String endpoint);

    void deleteByEndpointAndPanelUser_Id(String endpoint, UUID panelUserId);
}
