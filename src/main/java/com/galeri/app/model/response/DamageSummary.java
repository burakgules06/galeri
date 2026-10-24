package com.galeri.app.model.response;

// Listede tek satirda gostermek icin: kac parca lokal boyali / boyali / degisen.
public record DamageSummary(int locallyPainted, int painted, int replaced) {}
