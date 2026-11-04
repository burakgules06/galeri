package com.galeri.app.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// Konsinye araca cepten yapilan masraf (V26): sadece takip. Deftere yazilmaz, hicbir hesabi, kari ya da sermayeyi etkilemez.
@Entity
@Table(name = "vehicle_pocket_expense")
@Getter
@Setter
public class VehiclePocketExpense extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "expense_category_id")
    private UUID categoryId;

    @Column(length = 500)
    private String description;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;
}
