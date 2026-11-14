package com.galeri.app.model.response;

import java.util.UUID;

// Iki kisi arasindaki kalan borc: borrower, lender'a outstandingCents borclu (her zaman pozitif doner).
public record LoanBalance(UUID lenderUserId, String lenderName, UUID borrowerUserId, String borrowerName, long outstandingCents) {}
