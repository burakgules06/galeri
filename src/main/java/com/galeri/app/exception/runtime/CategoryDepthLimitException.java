package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;

// Masraf kategorileri en fazla 3 seviye olur (bkz. docs/MIMARI.md §7).
public class CategoryDepthLimitException extends ApplicationRuntimeException {
    public CategoryDepthLimitException() {
        super("Kategoriler en fazla 3 seviye olabilir", ErrorMessageConstant.CATEGORY_DEPTH_LIMIT);
    }
}
