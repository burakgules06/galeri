package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CatalogUnknownException;
import com.galeri.app.exception.runtime.ConsignmentDetailsRequiredException;
import com.galeri.app.exception.runtime.InvalidDateException;
import com.galeri.app.exception.runtime.InvalidSharesException;
import com.galeri.app.exception.runtime.PlateAlreadyInStockException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.exception.runtime.VehicleAlreadySoldException;
import com.galeri.app.exception.runtime.VehicleNotSoldException;
import com.galeri.app.model.entity.CatalogEntry;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.entity.VehiclePart;
import com.galeri.app.model.entity.VehicleShare;
import com.galeri.app.model.enums.BodyPart;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PartCondition;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.model.request.VehiclePartsRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.VehicleDetail;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.model.response.VehicleSummary;
import com.galeri.app.repository.CatalogEntryRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehiclePartRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.repository.VehicleShareRepository;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private VehiclePartRepository vehiclePartRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private PanelUserRepository panelUserRepository;
    @Mock private LedgerService ledgerService;
    @Mock private PaymentPlanService paymentPlanService;
    @Mock private VehicleShareRepository shareRepository;
    @Mock private CatalogEntryRepository catalogEntryRepository;
    private CatalogServiceImpl catalog;

    // "Su an": 1 Temmuz 2026, 09:00 Istanbul (06:00 UTC)
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T06:00:00Z"), ZoneOffset.UTC);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID cashId = UUID.randomUUID();
    private VehicleServiceImpl service;
    private Contact buyer;

    @BeforeEach
    void setUp() {
        catalog = new CatalogServiceImpl(catalogEntryRepository);   // gercek katalog (vehicles.json), eklemeler mock
        service = new VehicleServiceImpl(vehicleRepository, vehiclePartRepository, contactRepository, panelUserRepository,
                ledgerService, paymentPlanService, shareRepository, catalog, clock);
        buyer = new Contact();
        buyer.setId(UUID.randomUUID());
        buyer.setTenantId(tenantId);
        buyer.setName("Ayse Demir");
        TestSession.open(userId, tenantId, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    private VehicleRequest request(String plate) {
        return new VehicleRequest(plate, "Renault", "Clio", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL, 31000,
                "Kırmızı", null, null, LocalDate.of(2026, 6, 1), null, null, 72000000L, 60000000L, cashId, null,
                null, null, null, null, null, null, null, null);
    }

    private VehicleRequest makeModel(String make, String model, Boolean addToCatalog, String catalogModel) {
        return new VehicleRequest("34 KLM 4521", make, model, (short) 2022, Fuel.GASOLINE, Transmission.MANUAL, 31000,
                null, null, null, LocalDate.of(2026, 6, 1), null, null, 72000000L, null, cashId, null,
                null, null, null, null, null, null, addToCatalog, catalogModel);
    }

    // ------------------------------------------------------------------ katalog

    @Test
    void yeniArac_markaModelKatalogYazimina_cevrilir_paketKorunur() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(makeModel("volkswagen", "passat  1.5 TSI Business", null, null));

        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        assertThat(saved.getValue().getMake()).isEqualTo("Volkswagen");
        assertThat(saved.getValue().getModel()).isEqualTo("Passat 1.5 TSI Business");
    }

    @Test
    void katalogdaOlmayanMarkaModel_reddedilir() {
        assertThatThrownBy(() -> service.create(makeModel("Volkswagen", "Pasat", null, null))).isInstanceOf(CatalogUnknownException.class);
        assertThatThrownBy(() -> service.create(makeModel("Yugo", "45", null, null))).isInstanceOf(CatalogUnknownException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void listedeYok_onaylanirsa_galeriKatalogunaEklenir_aracKaydedilir() {
        when(catalogEntryRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(catalogEntryRepository.save(any(CatalogEntry.class))).thenAnswer(inv -> {
            CatalogEntry e = inv.getArgument(0);
            when(catalogEntryRepository.findByTenantId(tenantId)).thenReturn(List.of(e));   // sonraki okumalar eklemeyi gorur
            return e;
        });
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(makeModel("Yugo", "45 GL", true, "45"));

        ArgumentCaptor<CatalogEntry> entry = ArgumentCaptor.forClass(CatalogEntry.class);
        verify(catalogEntryRepository).save(entry.capture());
        assertThat(entry.getValue().getMake()).isEqualTo("Yugo");
        assertThat(entry.getValue().getModel()).isEqualTo("45");
        assertThat(entry.getValue().getTenantId()).isEqualTo(tenantId);
        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        assertThat(saved.getValue().getModel()).isEqualTo("45 GL");
    }

    @Test
    void katalogOncesiEskiKayit_markaModelDegismedikce_aynenKalir() {
        UUID id = UUID.randomUUID();
        Vehicle legacy = inStockVehicle(id, tenantId);
        legacy.setMake("BMW");
        legacy.setModel("320i M Sport");   // katalogda "3 Serisi"; eski kayit
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(legacy));

        service.update(id, makeModel("BMW", "320i M Sport", null, null));
        assertThat(legacy.getModel()).isEqualTo("320i M Sport");

        assertThatThrownBy(() -> service.update(id, makeModel("BMW", "320d", null, null))).isInstanceOf(CatalogUnknownException.class);
    }

    private Vehicle inStockVehicle(UUID id, UUID tenant) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setTenantId(tenant);
        v.setPlate("34 KLM 4521");
        v.setMake("Renault");
        v.setModel("Clio");
        v.setModelYear((short) 2022);
        v.setFuel(Fuel.GASOLINE);
        v.setTransmission(Transmission.MANUAL);
        v.setMileageKm(31000);
        v.setPurchaseDate(LocalDate.of(2026, 6, 1));
        v.setActualPurchaseCents(72000000L);
        return v;
    }

    @Test
    void yeniArac_plakaNormalizeEdilip_kaydedilir() {
        when(vehicleRepository.existsByTenantIdAndPlateAndStatus(tenantId, "34 KLM 4521", VehicleStatus.IN_STOCK)).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        VehicleDetailAdmin result = service.create(request("34klm4521"));

        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        assertThat(saved.getValue().getPlate()).isEqualTo("34 KLM 4521");
        assertThat(saved.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(result.vehicle().daysInStock()).isEqualTo(30);
        assertThat(result.actualPurchaseCents()).isEqualTo(72000000L);
        verify(paymentPlanService).syncPurchase(saved.getValue(), cashId, null);
    }

    @Test
    void aracGuncellenince_alisHareketiDeftereEsitlenir() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = inStockVehicle(id, tenantId);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));

        service.update(id, request("34 KLM 4521"));

        verify(paymentPlanService).syncPurchase(vehicle, cashId, null);
    }

    @Test
    void ayniPlakaStoktaVarsa_yeniArac_reddedilir() {
        when(vehicleRepository.existsByTenantIdAndPlateAndStatus(tenantId, "34 KLM 4521", VehicleStatus.IN_STOCK)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("34 KLM 4521"))).isInstanceOf(PlateAlreadyInStockException.class);
        verify(vehicleRepository, never()).save(any());
    }

    private VehicleSaleRequest sale(LocalDate date, long price) {
        return new VehicleSaleRequest(date, buyer.getId(), null, price,
                List.of(new InstallmentRequest(date, price, PaymentMethod.CASH, null, null, true, cashId)));
    }

    @Test
    void satis_araciSatildiYapar_planKurulur_ikinciSatis_reddedilir() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = inStockVehicle(id, tenantId);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(contactRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        VehicleSaleRequest request = sale(LocalDate.of(2026, 6, 20), 80000000L);
        VehicleDetailAdmin result = service.sell(id, request);

        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.SOLD);
        assertThat(vehicle.getBuyer()).isSameAs(buyer);
        assertThat(result.salePriceCents()).isEqualTo(80000000L);
        assertThat(result.vehicle().daysInStock()).isEqualTo(19);
        verify(paymentPlanService).recordSale(vehicle, buyer, request);
        assertThatThrownBy(() -> service.sell(id, sale(LocalDate.of(2026, 6, 21), 1L)))
                .isInstanceOf(VehicleAlreadySoldException.class);
    }

    @Test
    void satisTarihi_alistanOnceyse_reddedilir() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(inStockVehicle(id, tenantId)));

        assertThatThrownBy(() -> service.sell(id, sale(LocalDate.of(2026, 5, 31), 1L)))
                .isInstanceOf(InvalidDateException.class);
        verify(paymentPlanService, never()).recordSale(any(), any(), any());
    }

    @Test
    void satisGeriAlininca_aracStokaDoner_satisAlanlariTemizlenir() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = inStockVehicle(id, tenantId);
        vehicle.setStatus(VehicleStatus.SOLD);
        vehicle.setSaleDate(LocalDate.of(2026, 6, 20));
        vehicle.setBuyer(buyer);
        vehicle.setSalePriceCents(80000000L);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));

        service.undoSale(id);

        verify(paymentPlanService).undoSale(vehicle);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.IN_STOCK);
        assertThat(vehicle.getSaleDate()).isNull();
        assertThat(vehicle.getBuyer()).isNull();
        assertThat(vehicle.getSalePriceCents()).isNull();
    }

    @Test
    void stoktakiAracinSatisi_geriAlinamaz() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(inStockVehicle(id, tenantId)));

        assertThatThrownBy(() -> service.undoSale(id)).isInstanceOf(VehicleNotSoldException.class);
    }

    @Test
    void baskaGalerininAraci_bulunamadiGibiDavranir() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(inStockVehicle(id, UUID.randomUUID())));

        assertThatThrownBy(() -> service.detail(id)).isInstanceOf(SourceNotBelongToTenantException.class);
    }

    @Test
    void calisan_detaydaParaAlanlariniGormez() {
        TestSession.open(userId, tenantId, "STAFF");
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(inStockVehicle(id, tenantId)));

        Object result = service.detail(id);

        assertThat(result).isInstanceOf(VehicleDetail.class).isNotInstanceOf(VehicleDetailAdmin.class);
        assertThat(((VehicleDetail) result).parts()).hasSize(BodyPart.values().length)
                .allSatisfy((p, c) -> assertThat(c).isEqualTo(PartCondition.ORIGINAL));
    }

    @Test
    void yonetici_detaydaParaAlanlariniGorur() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(inStockVehicle(id, tenantId)));

        assertThat(service.detail(id)).isInstanceOf(VehicleDetailAdmin.class);
    }

    @Test
    void parcalar_orijinalOlanSilinir_digerleriEklenirYaDaGuncellenir() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(inStockVehicle(id, tenantId)));
        VehiclePart paintedHood = part(id, BodyPart.HOOD, PartCondition.PAINTED);
        VehiclePart replacedBumper = part(id, BodyPart.FRONT_BUMPER, PartCondition.REPLACED);
        when(vehiclePartRepository.findByVehicleId(id)).thenReturn(List.of(paintedHood, replacedBumper));

        Map<BodyPart, PartCondition> changes = new EnumMap<>(BodyPart.class);
        changes.put(BodyPart.HOOD, PartCondition.REPLACED);             // guncelle
        changes.put(BodyPart.FRONT_BUMPER, PartCondition.ORIGINAL);     // sil
        changes.put(BodyPart.ROOF, PartCondition.LOCALLY_PAINTED);      // ekle
        service.updateParts(id, new VehiclePartsRequest(changes));

        assertThat(paintedHood.getCondition()).isEqualTo(PartCondition.REPLACED);
        verify(vehiclePartRepository).delete(replacedBumper);
        ArgumentCaptor<VehiclePart> added = ArgumentCaptor.forClass(VehiclePart.class);
        verify(vehiclePartRepository).save(added.capture());
        assertThat(added.getValue().getPart()).isEqualTo(BodyPart.ROOF);
        assertThat(added.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void liste_hasarOzetiVeStoktaGunHesaplanir() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = inStockVehicle(id, tenantId);
        when(vehicleRepository.search(tenantId, null, "", true, "")).thenReturn(List.of(vehicle));
        when(vehiclePartRepository.findByVehicleIdIn(List.of(id))).thenReturn(List.of(
                part(id, BodyPart.FRONT_BUMPER, PartCondition.PAINTED),
                part(id, BodyPart.ROOF, PartCondition.PAINTED),
                part(id, BodyPart.HOOD, PartCondition.REPLACED)));

        List<VehicleSummary> list = service.search(null, null);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).damage().painted()).isEqualTo(2);
        assertThat(list.get(0).damage().replaced()).isEqualTo(1);
        assertThat(list.get(0).damage().locallyPainted()).isZero();
        assertThat(list.get(0).daysInStock()).isEqualTo(30);
    }

    @Test
    void yonetici_aliciAdiVeTelefonuylaArar_telefonBicimiNormalizeEdilir() {
        when(vehicleRepository.search(tenantId, VehicleStatus.SOLD, "0530 111 22 33", true, "5301112233")).thenReturn(List.of());

        service.search("0530 111 22 33", VehicleStatus.SOLD);

        verify(vehicleRepository).search(tenantId, VehicleStatus.SOLD, "0530 111 22 33", true, "5301112233");
    }

    @Test
    void calisan_kisiBilgisiyleAramaz() {
        TestSession.open(userId, tenantId, "STAFF");
        when(vehicleRepository.search(tenantId, null, "Ayse", false, "")).thenReturn(List.of());

        service.search("Ayse", null);

        verify(vehicleRepository).search(tenantId, null, "Ayse", false, "");
    }

    @Test
    void telefonAramasi_ucRakamdanKisaysaKapali() {
        assertThat(VehicleServiceImpl.phoneQuery("34 AB")).isEmpty();
        assertThat(VehicleServiceImpl.phoneQuery("passat 2021")).isEqualTo("2021");
        assertThat(VehicleServiceImpl.phoneQuery("+90 532 444")).isEqualTo("90532444");
    }

    private static VehiclePart part(UUID vehicleId, BodyPart p, PartCondition c) {
        VehiclePart vp = new VehiclePart();
        vp.setVehicleId(vehicleId);
        vp.setPart(p);
        vp.setCondition(c);
        return vp;
    }

    // ------------------------------------------------------------------ sahiplik

    private VehicleRequest ownershipRequest(Ownership ownership, UUID consignorId, CommissionType type, Integer bp, Long fixed,
                                            List<ShareRequest> shares) {
        return new VehicleRequest("34 KLM 4521", "Renault", "Clio", (short) 2022, Fuel.GASOLINE, Transmission.MANUAL, 31000,
                null, null, null, LocalDate.of(2026, 6, 1), null, null, 72000000L, 60000000L, cashId, null,
                ownership, consignorId, type, bp, fixed, shares, null, null);
    }

    private void savesWithId() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
    }

    private Contact contact(String name) {
        Contact c = new Contact();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setName(name);
        when(contactRepository.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void konsinyeArac_alisBedeliVeSaticisiTutulmaz_alisDeftereYazilmaz() {
        Contact owner = contact("Burak Gules");
        savesWithId();

        VehicleDetailAdmin result = service.create(ownershipRequest(Ownership.CONSIGNMENT, owner.getId(), CommissionType.RATE, 300, 999L, null));

        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        Vehicle v = saved.getValue();
        assertThat(v.getOwnership()).isEqualTo(Ownership.CONSIGNMENT);
        assertThat(v.getConsignor()).isSameAs(owner);
        assertThat(v.getCommissionRateBp()).isEqualTo(300);
        assertThat(v.getCommissionCents()).isNull();          // oran tipinde sabit tutar tutulmaz
        assertThat(v.getActualPurchaseCents()).isNull();
        assertThat(v.getOfficialPurchaseCents()).isNull();
        assertThat(v.getSeller()).isNull();
        assertThat(result.consignor().name()).isEqualTo("Burak Gules");
        verify(paymentPlanService).syncPurchase(v, null, null);   // kasa secilmis olsa da alis yazilmaz
    }

    @Test
    void konsinyede_sahipYaDaKomisyonYoksa_reddedilir() {
        assertThatThrownBy(() -> service.create(ownershipRequest(Ownership.CONSIGNMENT, null, CommissionType.RATE, 300, null, null)))
                .isInstanceOf(ConsignmentDetailsRequiredException.class);
        assertThatThrownBy(() -> service.create(ownershipRequest(Ownership.CONSIGNMENT, UUID.randomUUID(), CommissionType.FIXED, 300, null, null)))
                .isInstanceOf(ConsignmentDetailsRequiredException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void ortakPaylari_toplami10000DegilseYaDaKisiTekrarliysa_reddedilir() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<ShareRequest> eksik = List.of(new ShareRequest(a, 5000), new ShareRequest(b, 4000));
        List<ShareRequest> fazla = List.of(new ShareRequest(a, 6000), new ShareRequest(b, 5000));
        List<ShareRequest> tekrar = List.of(new ShareRequest(a, 5000), new ShareRequest(a, 5000));

        for (List<ShareRequest> shares : List.of(eksik, fazla, tekrar, List.<ShareRequest>of())) {
            assertThatThrownBy(() -> service.create(ownershipRequest(Ownership.CO_OWNED, null, null, null, null, shares)))
                    .isInstanceOf(InvalidSharesException.class);
        }
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void ortakliArac_paylarKaydedilir_alisNormalYazilir() {
        Contact furkan = contact("Furkan");
        Contact selin = contact("Selin");
        savesWithId();

        service.create(ownershipRequest(Ownership.CO_OWNED, null, null, null, null,
                List.of(new ShareRequest(furkan.getId(), 5000), new ShareRequest(selin.getId(), 5000))));

        ArgumentCaptor<VehicleShare> shares = ArgumentCaptor.forClass(VehicleShare.class);
        verify(shareRepository, times(2)).save(shares.capture());
        assertThat(shares.getAllValues()).extracting(VehicleShare::getShareBp).containsExactly(5000, 5000);
        assertThat(shares.getAllValues()).extracting(s -> s.getContact().getId()).containsExactly(furkan.getId(), selin.getId());
        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        verify(paymentPlanService).syncPurchase(saved.getValue(), cashId, null);
    }

    @Test
    void satis_konsinyeBorcunuEsitler_satisGeriAlininca_yeniden() {
        UUID id = UUID.randomUUID();
        Vehicle v = inStockVehicle(id, tenantId);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(v));
        when(contactRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        service.sell(id, new VehicleSaleRequest(LocalDate.of(2026, 6, 20), buyer.getId(), null, 80000000L,
                List.of(new InstallmentRequest(LocalDate.of(2026, 6, 20), 80000000L, PaymentMethod.CASH, null, null, true, cashId))));
        service.undoSale(id);

        verify(ledgerService, times(2)).syncConsignmentSettlement(v);
    }
}
