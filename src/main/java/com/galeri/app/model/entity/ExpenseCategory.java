package com.galeri.app.model.entity;

import com.galeri.app.model.enums.ExpenseRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Masraf kategorisi. Ayri ekrani yok, masraf girerken yazilan adla olusur.
@Entity
@Table(name = "expense_category")
@Getter
@Setter
public class ExpenseCategory extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ExpenseRoot root;

    @Column(nullable = false)
    private boolean archived = false;
}
