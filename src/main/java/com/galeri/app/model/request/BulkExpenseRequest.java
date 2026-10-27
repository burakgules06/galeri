package com.galeri.app.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// Toplu masraf: satirlarin hepsi tek transaction'da yazilir; biri hataliysa hicbiri yazilmaz.
public record BulkExpenseRequest(@NotEmpty @Size(max = 100) List<@Valid ExpenseRequest> items) {}
