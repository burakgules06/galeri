package com.galeri.app.service;

// Ilk galeriyi ve yoneticisini ortam degiskenlerinden olusturur (bkz. docs/DURUM.md "Prod kurulumu").
public interface BootstrapService {

    enum Result { NOT_CONFIGURED, INCOMPLETE, INVALID, SKIPPED, CREATED }

    /** Hic galeri yoksa ve tum BOOTSTRAP_* degiskenleri doluysa galeri + ADMIN olusturur. Tekrar cagrilmasi guvenli. */
    Result run();
}
