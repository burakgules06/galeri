package com.galeri.app.repository;

import com.galeri.app.model.entity.Account;
import com.galeri.app.model.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findFirstByTenantIdAndType(UUID tenantId, AccountType type);

    Optional<Account> findByVehicleId(UUID vehicleId);

    Optional<Account> findFirstByTenantIdAndTypeAndContactId(UUID tenantId, AccountType type, UUID contactId);

    List<Account> findByTenantIdAndTypeInOrderByCreatedAtAsc(UUID tenantId, Collection<AccountType> types);
}
