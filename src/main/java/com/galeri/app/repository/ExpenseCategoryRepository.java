package com.galeri.app.repository;

import com.galeri.app.model.entity.ExpenseCategory;
import com.galeri.app.model.enums.ExpenseRoot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    // Ayni ad harf buyuklugunden bagimsiz ayni kategori sayilir. Ust null ve dolu icin ayri
    // metot: null UUID parametresi PostgreSQL tip cikarimini bozabiliyor.
    Optional<ExpenseCategory> findFirstByTenantIdAndRootAndParentIdIsNullAndNameIgnoreCase(UUID tenantId, ExpenseRoot root, String name);

    Optional<ExpenseCategory> findFirstByTenantIdAndRootAndParentIdAndNameIgnoreCase(UUID tenantId, ExpenseRoot root, UUID parentId, String name);

    List<ExpenseCategory> findByTenantIdAndRootAndArchivedFalse(UUID tenantId, ExpenseRoot root);
}
