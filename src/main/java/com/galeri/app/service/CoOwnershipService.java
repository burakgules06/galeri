package com.galeri.app.service;

import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.request.OwnerReturnRequest;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.response.ShareResponse;
import com.galeri.app.model.response.VehicleRef;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Ortak finansmani (MIMARI §4 Sahiplik, §5): ortak aracin sahipleri (Havuz ve/veya ortaklar), katkilari, kar paylari ve
// ortaga donus. Ortagin sermayesi = nakitteki + araclarda bagli; bagli kisim hicbir yerde saklanmaz, odenmis alis
// odemelerinden hesaplanir. Zamanlama CoOwnerSettlementPolicy'den (bugun AT_SALE).
public interface CoOwnershipService {

    /** Sahip satirlarini istekle esitler (dogrulama dahil); ortak olmayan kisi ortak yapilir. Ortak arac degilse siler. */
    void syncOwners(Vehicle vehicle, List<ShareRequest> owners);

    /**
     * Ortaklarin katkisini aracin odenmis alis odemelerine esitler (pesin alis ya da odenen her taksit, sahiplik payina gore;
     * kurus farki Havuz'a). Cepten kisim icin o odemenin kasa / bankasina otomatik sermaye girisi yazilir. Sonunda
     * ortaklarin nakitteki sermayesi eksiye dusuyorsa 1042. Satilmis aracta kar paylari da yeniden hesaplanir.
     */
    void syncContributions(Vehicle vehicle);

    /** Satista: kar paylari (VEHICLE_PROFIT_SHARE) ve ortaga donus (nakit kisim icin sermaye cekisi). */
    void onSale(Vehicle vehicle, List<OwnerReturnRequest> returns);

    /** Satis geri alinirken: nakit donus yapilmissa 1043; kar paylari terslenir. */
    void onUndoSale(Vehicle vehicle);

    /**
     * Satilmis ortakli / konsinye aracin tahsilat dagitimini defterle esitler: her tahsilat (pesin dahil) geldikce ortaklarin
     * kar payi oranlariyla bolunur (konsinyede sahibinin alacagi oraninda) ve sermayelerine yazilir. Satis, tahsilat, geri alma,
     * ciro, karsiliksiz ve masraf degisikliginden sonra cagrilir.
     */
    void resyncProfitShares(Vehicle vehicle);

    /**
     * Araclarin parasi kimin: ortakli aracta sahipler, konsinyede sahibi, galeri aracinda dukkan sahibi (havuz).
     * Odemeler ekraninda kisi secimi ve sermayedarin kendi kayitlari icin.
     */
    Map<UUID, List<UUID>> vehicleUsers(UUID tenantId, java.util.Collection<UUID> vehicleIds);

    /**
     * Kasa / banka hareketlerinin sermayedarlara dusen kismi (hareket id -> kullanici -> kurus, hesaba giren arti): ortakli
     * aracin alis odemesinde ortagin katkisi (eksi), ortakli / konsinye satis tahsilatinda sahibine gecen pay (arti).
     */
    Map<UUID, Map<UUID, Long>> moneyEntryShares(UUID tenantId);

    /** Dukkan sahibi (havuz): galerinin ilk aktif yoneticisi; yoksa null. */
    UUID poolOwnerId(UUID tenantId);

    /** Satilmis araclardan sahiplerin henuz tahsil edilmemis alacagi (sermayedarin kartinda alacak). */
    List<PendingShare> pendingSaleShares(UUID tenantId);

    record PendingShare(UUID userId, VehicleRef vehicle, long cents) {}

    /** Ortakli araçta ortaklarin (dukkan sahibi haric) toplam kari: alacak - katki. Donem karinda havuzdan ayrilir. */
    long partnersProfit(Vehicle vehicle);

    /** Satilmis konsinye araçta sahibinin alacagi ve tahsil edilip sermayesine gecen kismi; degilse null. */
    ConsignmentShare consignmentShare(Vehicle vehicle);

    record ConsignmentShare(long entitledCents, long collectedCents) {}

    /** Ortak (contactId) basina araclarda bagli sermaye: asOf gunu itibariyle stoktaki ortak araclardaki odenmis katkilar. */
    Map<UUID, Long> boundCapital(UUID tenantId, LocalDate asOf);

    /** Aracin sahipleri gosterim icin: paylar, katki, satildiysa kar payi ve alacak. Ortak arac degilse bos. */
    List<ShareResponse> owners(Vehicle vehicle);

    /**
     * Kasa'dan tek basina duzeltilemeyecek ortak hareketi mi: kar payi ya da cepten katki (alis odemesine bagli; ikisi de
     * arac / taksit islemleriyle kendiliginden esitlenir). Ortaga nakit donus cekisi bagli degildir, duzeltilebilir.
     */
    boolean isLinkedEntry(LedgerEntry entry);
}
