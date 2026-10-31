package com.galeri.app.repository;

import com.galeri.app.model.entity.Account;
import com.galeri.app.model.enums.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findFirstByTenantIdAndType(UUID tenantId, AccountType type);

    Optional<Account> findByVehicleId(UUID vehicleId);

    Optional<Account> findFirstByTenantIdAndTypeAndContactId(UUID tenantId, AccountType type, UUID contactId);

    Optional<Account> findFirstByTenantIdAndTypeAndUserId(UUID tenantId, AccountType type, UUID userId);

    List<Account> findByTenantIdAndTypeInOrderByCreatedAtAsc(UUID tenantId, Collection<AccountType> types);

    /**
     * Hesap satirini kilitler (SELECT ... FOR UPDATE). Bakiyeye bakip hareket yazan islemler (sermaye cekisi) sirayla calissin:
     * iki es zamanli cekis ayni bakiyeyi gorup sermayeyi eksiye dusurmesin.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") UUID id);
}
