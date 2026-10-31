package com.galeri.app.repository;

import com.galeri.app.model.entity.PanelUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PanelUserRepository extends JpaRepository<PanelUser, UUID> {
    Optional<PanelUser> findByPhoneAndActiveTrue(String phone);

    List<PanelUser> findByTenant_IdOrderByFullName(UUID tenantId);

    boolean existsByPhone(String phone);

    /** Her istekte: kullanici hala aktif mi, guncel rolu ne (token'daki rol eskimis olabilir). */
    @Query("select u.role from PanelUser u where u.id = :id and u.active = true and u.canLogin = true")
    Optional<String> findActiveRole(@Param("id") UUID id);

    long countByTenant_IdAndRoleAndActiveTrue(UUID tenantId, String role);

    /** Sermayesi olabilen kullanicilar (dukkan sahibi, sermayedar), eklenme sirasiyla. */
    @Query("select u from PanelUser u where u.tenant.id = :tenantId and u.role in ('ADMIN', 'INVESTOR') order by u.createdAt, u.id")
    List<PanelUser> findCapitalHolders(@Param("tenantId") UUID tenantId);

    /**
     * Havuzdan kalanin sahibi: en once acilan aktif dukkan sahibi (simdilik tek; birden fazla dukkan sahibinde kalanin
     * paylasimi ileride tanimlanacak).
     */
    @Query("select u from PanelUser u where u.tenant.id = :tenantId and u.role = 'ADMIN' order by u.active desc, u.createdAt, u.id limit 1")
    Optional<PanelUser> findPoolOwner(@Param("tenantId") UUID tenantId);
}
