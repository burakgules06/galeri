package com.galeri.app.repository;

import com.galeri.app.model.entity.Hareket;
import com.galeri.app.model.enums.HareketTur;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HareketRepository extends JpaRepository<Hareket, UUID> {

    // Hesap bazinda toplamlar: [hesapId, toplamKurus]
    @Query("select h.hedefHesap.id, sum(h.tutarKurus) from Hareket h where h.tenantId = :tenantId group by h.hedefHesap.id")
    List<Object[]> girenToplamlari(@Param("tenantId") UUID tenantId);

    @Query("select h.kaynakHesap.id, sum(h.tutarKurus) from Hareket h where h.tenantId = :tenantId group by h.kaynakHesap.id")
    List<Object[]> cikanToplamlari(@Param("tenantId") UUID tenantId);

    @Query("""
            select h from Hareket h
            join fetch h.kaynakHesap join fetch h.hedefHesap join fetch h.olusturan
            left join fetch h.arac left join fetch h.kategori
            where h.tenantId = :tenantId
            order by h.tarih desc, h.createdAt desc
            """)
    List<Hareket> liste(@Param("tenantId") UUID tenantId, Pageable sayfa);

    @Query("""
            select h from Hareket h
            join fetch h.kaynakHesap join fetch h.hedefHesap join fetch h.olusturan
            left join fetch h.arac left join fetch h.kategori
            where h.tenantId = :tenantId and (h.kaynakHesap.id = :hesapId or h.hedefHesap.id = :hesapId)
            order by h.tarih desc, h.createdAt desc
            """)
    List<Hareket> hesapHareketleri(@Param("tenantId") UUID tenantId, @Param("hesapId") UUID hesapId, Pageable sayfa);

    @Query("""
            select h from Hareket h
            join fetch h.kaynakHesap join fetch h.hedefHesap join fetch h.olusturan
            left join fetch h.kategori
            where h.arac.id = :aracId
            order by h.tarih, h.createdAt
            """)
    List<Hareket> aracHareketleri(@Param("aracId") UUID aracId);

    boolean existsByDuzeltilenHareketId(UUID hareketId);

    @Query("select h.duzeltilenHareketId from Hareket h where h.duzeltilenHareketId in :idler")
    List<UUID> duzeltilenler(@Param("idler") Collection<UUID> idler);

    List<Hareket> findByArac_IdAndTur(UUID aracId, HareketTur tur);

    // Kategori kullanim sayisi (hizli butonlar icin), duzeltmeler sayilmaz: [kategoriId, adet]
    @Query("""
            select h.kategori.id, count(h) from Hareket h
            where h.tenantId = :tenantId and h.kategori is not null and h.duzeltilenHareketId is null
            group by h.kategori.id
            """)
    List<Object[]> kategoriKullanimlari(@Param("tenantId") UUID tenantId);
}
