package com.galeri.app.service;

import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.request.OwnerReturnRequest;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.response.ShareResponse;

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

    /** Satilmis ortak aracin kari degistiyse (masraf, alis bedeli, sahipler) kar paylarini yeniden yazar. */
    void resyncProfitShares(Vehicle vehicle);

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
