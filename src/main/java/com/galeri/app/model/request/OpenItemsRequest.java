package com.galeri.app.model.request;

import com.galeri.app.model.enums.PlanDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// Gecmis senet / cek aktarimi (aracsiz): SALE = kisi bize borclu (alinan senet / cek), PURCHASE = biz borcluyuz (verilen).
// date: acilis hareketinin tarihi (genelde bugun). Satirlarda paidNow ve accountId yok sayilir; hepsi acik girer.
public record OpenItemsRequest(
        @NotNull PlanDirection direction,
        @NotNull LocalDate date,
        @NotEmpty @Size(max = 60) List<@Valid InstallmentRequest> installments
) {}
