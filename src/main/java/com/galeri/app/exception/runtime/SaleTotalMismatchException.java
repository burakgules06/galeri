package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Odeme planindaki taksitlerin toplami satis bedeline kurusu kurusuna esit degil.
public class SaleTotalMismatchException extends ApplicationRuntimeException {
    public SaleTotalMismatchException() {
        super("Ödeme planının toplamı satış bedeline eşit olmalı", ErrorMessageConstant.SALE_TOTAL_MISMATCH);
    }
}
