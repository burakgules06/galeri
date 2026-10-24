package com.galeri.app.model.entity;

import com.galeri.app.model.enums.HareketTur;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Defter kaydi: kaynak hesaptan hedef hesaba tutar (kurus). Silinmez, degismez (DB trigger'i da reddeder).
// Duzeltme: ayni tutarda ters yonde yeni hareket, duzeltilenHareketId ile baglanir.
@Entity
@Immutable
@Table(name = "hareket")
@Getter
@Setter
public class Hareket {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private LocalDate tarih;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private HareketTur tur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kaynak_hesap_id", updatable = false)
    private Hesap kaynakHesap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hedef_hesap_id", updatable = false)
    private Hesap hedefHesap;

    @Column(name = "tutar_kurus", nullable = false, updatable = false)
    private Long tutarKurus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arac_id", updatable = false)
    private Arac arac;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "masraf_kategori_id", updatable = false)
    private MasrafKategori kategori;

    @Column(length = 500, updatable = false)
    private String aciklama;

    @Column(name = "duzeltilen_hareket_id", updatable = false)
    private UUID duzeltilenHareketId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "olusturan_kullanici_id", updatable = false)
    private PanelUser olusturan;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
