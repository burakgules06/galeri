package com.galeri.app.model.response;

import java.time.LocalDate;
import java.util.UUID;

// Konsinye araca cepten yapilan masraf (sadece takip; parasal etkisi yok). Tutar kurus.
public record PocketExpenseResponse(UUID id, UUID vehicleId, LocalDate date, long amountCents, UUID categoryId, String categoryName,
                                    String description) {}
