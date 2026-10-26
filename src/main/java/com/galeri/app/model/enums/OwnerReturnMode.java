package com.galeri.app.model.enums;

// Ortak arac satilinca ortagin alacagi (katki + kar payi) ne olur: sermayede kalir, nakit odenir (otomatik sermaye
// cekisi) ya da bir kismi nakit odenir.
public enum OwnerReturnMode {
    KEEP, CASH, HYBRID
}
