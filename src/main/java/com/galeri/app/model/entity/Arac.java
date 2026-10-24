package com.galeri.app.model.entity;

import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.enums.Sahiplik;
import com.galeri.app.model.enums.Vites;
import com.galeri.app.model.enums.Yakit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// Tutarlar kurus cinsinden (BIGINT). Para alanlari STAFF'a asla donmez (bkz. AracDetayAdmin / AracDetay).
@Entity
@Table(name = "arac")
@Getter
@Setter
public class Arac extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 12)
    private String plaka;

    @Column(nullable = false, length = 40)
    private String marka;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "model_yili", nullable = false)
    private Short modelYili;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Yakit yakit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Vites vites;

    @Column(nullable = false)
    private Integer km = 0;

    @Column(length = 30)
    private String renk;

    @Column(name = "tramer_kurus")
    private Long tramerKurus;

    @Column(columnDefinition = "text")
    private String notlar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Sahiplik sahiplik = Sahiplik.GALERI;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AracDurum durum = AracDurum.STOKTA;

    // ---- alis ----
    @Column(name = "alis_tarihi", nullable = false)
    private LocalDate alisTarihi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "satici_id")
    private Kisi satici;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alan_calisan_id")
    private PanelUser alanCalisan;

    @Column(name = "gercek_alis_kurus")
    private Long gercekAlisKurus;

    @Column(name = "resmi_alis_kurus")
    private Long resmiAlisKurus;

    // ---- satis ----
    @Column(name = "satis_tarihi")
    private LocalDate satisTarihi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alici_id")
    private Kisi alici;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "satan_calisan_id")
    private PanelUser satanCalisan;

    @Column(name = "satis_kurus")
    private Long satisKurus;
}
