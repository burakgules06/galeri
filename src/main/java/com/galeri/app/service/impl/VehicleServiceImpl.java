package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.PocketPurchaseNotAllowedException;
import com.galeri.app.exception.runtime.StaffCashOnlyException;
import com.galeri.app.exception.runtime.InvalidBasketException;
import com.galeri.app.exception.runtime.InvalidUserCounterpartyException;
import com.galeri.app.exception.runtime.TransferLockedException;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ShareRequest;
import com.galeri.app.service.CapitalService;
import com.galeri.app.exception.runtime.CatalogUnknownException;
import com.galeri.app.exception.runtime.ConsignmentDetailsRequiredException;
import com.galeri.app.exception.runtime.InvalidDateException;
import com.galeri.app.exception.runtime.InvalidShopCutException;
import com.galeri.app.exception.runtime.PlateAlreadyInStockException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.exception.runtime.VehicleAlreadySoldException;
import com.galeri.app.exception.runtime.VehicleNotRemovableException;
import com.galeri.app.exception.runtime.VehicleNotSoldException;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.entity.VehiclePart;
import com.galeri.app.model.enums.BodyPart;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.PartCondition;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.enums.ContributionSource;
import com.galeri.app.exception.runtime.InvalidSharesException;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.OwnerReturnRequest;
import org.springframework.security.access.AccessDeniedException;
import com.galeri.app.model.request.BasketSaleRequest;
import com.galeri.app.model.request.VehiclePartsRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.ContactSummary;
import com.galeri.app.model.response.DamageSummary;
import com.galeri.app.model.response.StaffSummary;
import com.galeri.app.model.response.VehicleDetail;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.model.response.VehicleSummary;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehiclePartRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CatalogService;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.util.PlateUtil;
import com.galeri.app.util.NameUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    // Tek galeri Turkiye'de; tenant.timezone ileride kullanilabilir.
    static final ZoneId DEALER_ZONE = ZoneId.of("Europe/Istanbul");

    private final VehicleRepository vehicleRepository;
    private final VehiclePartRepository vehiclePartRepository;
    private final ContactRepository contactRepository;
    private final PanelUserRepository panelUserRepository;
    private final LedgerService ledgerService;
    private final PaymentPlanService paymentPlanService;
    private final CatalogService catalogService;
    private final CoOwnershipService coOwnershipService;
    private final CapitalService capitalService;
    private final Clock clock;

    // ------------------------------------------------------------------ okuma

    @Override
    @Transactional(readOnly = true)
    public List<VehicleSummary> search(String q, VehicleStatus status) {
        String query = q == null ? "" : q.trim();
        boolean admin = CurrentUser.isAdmin();
        List<Vehicle> vehicles = vehicleRepository.search(CurrentUser.tenantId(), status, query, admin,
                admin ? phoneQuery(query) : "");
        if (vehicles.isEmpty()) return List.of();

        Map<UUID, List<VehiclePart>> parts = vehiclePartRepository
                .findByVehicleIdIn(vehicles.stream().map(Vehicle::getId).toList()).stream()
                .collect(Collectors.groupingBy(VehiclePart::getVehicleId));

        LocalDate today = today();
        return vehicles.stream()
                .map(v -> toSummary(v, parts.getOrDefault(v.getId(), List.of()), today, admin))
                .toList();
    }

    /**
     * Telefonla arama icin rakamlar: "0530 111 22 33" -> "5301112233" (kayitli bicim 90XXXXXXXXXX'in parcasi).
     * 3 rakamdan kisaysa telefonla aranmaz (plaka ve model yilindaki rakamlarla karismasin).
     */
    static String phoneQuery(String q) {
        String digits = q.replaceAll("\\D", "").replaceFirst("^0+", "");
        return digits.length() >= 3 ? digits : "";
    }

    @Override
    @Transactional(readOnly = true)
    public Object detail(UUID id) {
        Vehicle vehicle = getVehicle(id);
        VehicleDetail detail = toDetail(vehicle);
        // Sermayedar kendi (ve ortak oldugu) aracini fiyatlariyla gorur; digerlerini calisan gibi
        return CurrentUser.isAdmin() || investorOwns(vehicle) ? toAdmin(vehicle, detail) : detail;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAccess(UUID vehicleId) {
        if (CurrentUser.isAdmin()) return;
        if (!investorOwns(getVehicle(vehicleId))) throw new AccessDeniedException("Bu arac sermayedara ait degil");
    }

    /** Oturumdaki sermayedar bu aracin sahiplerinden mi (ortakli aracta payi var ya da konsinye sahibi). */
    private boolean investorOwns(Vehicle vehicle) {
        if (!CurrentUser.isInvestor()) return false;
        UUID me = CurrentUser.principal().userId();
        return coOwnershipService.vehicleUsers(vehicle.getTenantId(), List.of(vehicle.getId()))
                .getOrDefault(vehicle.getId(), List.of()).contains(me);
    }

    /**
     * Sermayedarin alimi: arac her zaman ortakli (tek basina aldiysa %100 onun), payi kendi sermayesinden (katki kaynagi
     * sermaye); para galerinin kasasindan cikar, kasa secmez. Senetli alis, konsinye ve dukkan payi yok.
     */
    /**
     * Yetkili calisan (can_trade, StaffTradeGuard): sadece pesin, galeri adina alim. Satici kisi, tamami secilen kasa /
     * bankadan; plan, konsinye, ortaklik, kullanicidan alim yok. Alan calisan kendisi.
     */
    private VehicleRequest staffPurchase(VehicleRequest r) {
        boolean dealer = r.ownership() == null || r.ownership() == Ownership.DEALER;
        if (!dealer || r.sellerId() == null || r.sellerUserId() != null || r.actualPurchaseCents() == null
                || (r.purchasePlan() != null && !r.purchasePlan().isEmpty()) || !isMoneyAccount(r.purchaseAccountId())) {
            throw new StaffCashOnlyException();
        }
        UUID me = CurrentUser.principal().userId();
        return new VehicleRequest(r.plate(), r.make(), r.model(), r.modelYear(), r.fuel(), r.transmission(), r.mileageKm(), r.color(),
                r.tramerCents(), r.notes(), r.purchaseDate(), r.sellerId(), me, r.actualPurchaseCents(), r.officialPurchaseCents(),
                r.purchaseAccountId(), null, Ownership.DEALER, null, null, null, null, null, r.addToCatalog(),
                r.catalogModel(), null, null, null, null, null);
    }

    /**
     * Yetkili calisan: sadece pesin satis. Alici kisi; her satir simdi odenmis ve bir kasa / banka hesabina giriyor.
     * Kullaniciya satis, devir ve ortaga donus secimi yok (ortaklarin payi varsayilanla sermayede kalir). Satan calisan kendisi.
     */
    private VehicleSaleRequest staffSale(VehicleSaleRequest r) {
        if (r.buyerId() == null || r.buyerUserId() != null || r.transferOrDefault() || r.installments() == null || r.installments().isEmpty()
                || r.installments().stream().anyMatch(i -> !i.paidNow() || !isMoneyAccount(i.accountId())
                        || i.endorseInstallmentId() != null || i.payerId() != null)) {
            throw new StaffCashOnlyException();
        }
        UUID me = CurrentUser.principal().userId();
        return new VehicleSaleRequest(r.saleDate(), r.buyerId(), me, r.salePriceCents(), r.installments(), null);
    }

    private boolean isMoneyAccount(UUID accountId) {
        return accountId != null && ledgerService.moneyAccounts().stream().anyMatch(a -> a.id().equals(accountId));
    }

    private VehicleRequest investorPurchase(VehicleRequest r) {
        UUID me = CurrentUser.principal().userId();
        if (r.ownershipOrDefault() != Ownership.CO_OWNED || r.shares() == null
                || r.shares().stream().noneMatch(s -> me.equals(s.userId()))
                || r.shares().stream().anyMatch(s -> s.sourceOrDefault() != ContributionSource.CAPITAL)
                || (r.purchasePlan() != null && !r.purchasePlan().isEmpty())) {
            throw new InvalidSharesException();
        }
        return new VehicleRequest(r.plate(), r.make(), r.model(), r.modelYear(), r.fuel(), r.transmission(), r.mileageKm(), r.color(),
                r.tramerCents(), r.notes(), r.purchaseDate(), r.sellerId(), me, r.actualPurchaseCents(), r.officialPurchaseCents(),
                ledgerService.cashAccountId(), null, Ownership.CO_OWNED, null, null, null, null, r.shares(), r.addToCatalog(),
                r.catalogModel(), null, null, null, r.sellerUserId(), r.sellerCapitalCents());
    }

    /**
     * Sermayedarin satisi: kendi aracini satar, ayni odeme secenekleri; "simdi alindi" para galerinin kasasina girer.
     * Ortaga donusten sadece kendisininki: "Nakit aldım" (payi cebine) ya da "Sermayeme ekle".
     */
    private VehicleSaleRequest investorSale(VehicleSaleRequest r) {
        UUID me = CurrentUser.principal().userId();
        UUID cash = ledgerService.cashAccountId();
        List<InstallmentRequest> rows = r.installments() == null ? List.of() : r.installments().stream().map(i -> new InstallmentRequest(i.dueDate(), i.amountCents(), i.method(),
                i.noteNo(), i.payerId(), i.paidNow(), i.paidNow() ? cash : null, i.bankName(), i.drawerName(), i.description())).toList();
        List<OwnerReturnRequest> returns = r.ownerReturns() == null ? null : r.ownerReturns().stream()
                .filter(o -> me.equals(o.userId()))
                .map(o -> new OwnerReturnRequest(o.userId(), o.mode(), o.amountCents(), cash)).toList();
        List<InstallmentRequest> safeRows = r.installments() == null ? null : rows;
        return new VehicleSaleRequest(r.saleDate(), r.buyerId(), r.soldById(), r.salePriceCents(), safeRows, returns,
                r.buyerUserId(), r.transfer(), r.buyerCapitalCents(), r.buyerUserId() == null ? null : cash);
    }

    // ------------------------------------------------------------------ yazma

    @Override
    @Transactional
    public VehicleDetailAdmin create(VehicleRequest request) {
        UUID tenantId = CurrentUser.tenantId();
        if (CurrentUser.isInvestor()) request = investorPurchase(request);
        if (CurrentUser.isStaff()) request = staffPurchase(request);
        String plate = PlateUtil.normalize(request.plate());
        if (vehicleRepository.existsByTenantIdAndPlateAndStatus(tenantId, plate, VehicleStatus.IN_STOCK)) {
            throw new PlateAlreadyInStockException();
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setTenantId(tenantId);
        vehicle.setPlate(plate);
        apply(vehicle, request);
        Vehicle saved = vehicleRepository.save(vehicle);
        coOwnershipService.syncOwners(saved, request.shares());
        preparePocket(saved, request);
        syncPurchase(saved, request);
        syncSellerCapital(saved, request);
        syncPocket(saved);
        coOwnershipService.syncContributions(saved);   // ortak aracta cepten katki ve nakit sermaye kontrolu
        if (saved.getActualPurchaseCents() != null) coOwnershipService.checkConsignorCash(saved);   // konsinye: sermayeden kisim yetmeli
        return toAdmin(saved, toDetail(saved));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin update(UUID id, VehicleRequest request) {
        Vehicle vehicle = getVehicle(id);
        String plate = PlateUtil.normalize(request.plate());
        boolean plateChanged = !plate.equals(vehicle.getPlate());
        if (plateChanged && vehicle.getStatus() == VehicleStatus.IN_STOCK
                && vehicleRepository.existsByTenantIdAndPlateAndStatus(vehicle.getTenantId(), plate, VehicleStatus.IN_STOCK)) {
            throw new PlateAlreadyInStockException();
        }
        if (vehicle.getTransferFromVehicleId() != null && transferPurchaseChanged(vehicle, request)) {
            throw new TransferLockedException("Bu araç devirle geldi; alış bedeli, tarihi ve sahipliği değiştirilemez. Önce eski kaydın satışını geri alın.");
        }
        // Konsinye maliyeti / sahibi / cepten kismi degistiyse sahibinin nakitteki parasi yeniden kontrol edilir
        List<Object> consignmentBefore = consignmentFunding(vehicle, vehicle.getPurchaseCapitalEntryId());
        vehicle.setPlate(plate);
        apply(vehicle, request);
        if (vehicle.getSaleDate() != null && vehicle.getSaleDate().isBefore(vehicle.getPurchaseDate())) {
            throw new InvalidDateException();
        }
        coOwnershipService.syncOwners(vehicle, request.shares());
        preparePocket(vehicle, request);
        syncPurchase(vehicle, request);
        syncSellerCapital(vehicle, request);
        syncPocket(vehicle);
        if (vehicle.getActualPurchaseCents() != null
                && !consignmentBefore.equals(consignmentFunding(vehicle, vehicle.getPurchaseCapitalEntryId()))) {
            coOwnershipService.checkConsignorCash(vehicle);
        }
        // Ortak aracta katkilar; satilmissa kar paylari da yeniden yazilir
        coOwnershipService.syncContributions(vehicle);
        // Satilmis aracta sahiplik ya da komisyon degistiyse sahibine borc yeniden yazilir
        if (vehicle.getStatus() != VehicleStatus.IN_STOCK) ledgerService.syncConsignmentSettlement(vehicle);
        return toAdmin(vehicle, toDetail(vehicle));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin updateParts(UUID id, VehiclePartsRequest request) {
        Vehicle vehicle = getVehicle(id);
        Map<BodyPart, VehiclePart> existing = vehiclePartRepository.findByVehicleId(vehicle.getId()).stream()
                .collect(Collectors.toMap(VehiclePart::getPart, Function.identity()));

        // Yerinde guncelleme: sil + ekle yapilmaz (Hibernate insert'leri delete'lerden once
        // calistirdigi icin (vehicle_id, part) tekillik kisiti patlar).
        request.parts().forEach((part, condition) -> {
            VehiclePart record = existing.get(part);
            if (condition == PartCondition.ORIGINAL) {
                if (record != null) vehiclePartRepository.delete(record);
            } else if (record != null) {
                record.setCondition(condition);
            } else {
                VehiclePart created = new VehiclePart();
                created.setTenantId(vehicle.getTenantId());
                created.setVehicleId(vehicle.getId());
                created.setPart(part);
                created.setCondition(condition);
                vehiclePartRepository.save(created);
            }
        });
        vehiclePartRepository.flush();
        return toAdmin(vehicle, toDetail(vehicle));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin sell(UUID id, VehicleSaleRequest request) {
        Vehicle vehicle = getVehicle(id);
        if (CurrentUser.isInvestor()) {
            if (!investorOwns(vehicle)) throw new AccessDeniedException("Bu arac sermayedara ait degil");
            request = investorSale(request);
        }
        if (CurrentUser.isStaff()) request = staffSale(request);
        if (vehicle.getStatus() != VehicleStatus.IN_STOCK) throw new VehicleAlreadySoldException();
        if (request.saleDate().isBefore(vehicle.getPurchaseDate())) throw new InvalidDateException();
        if (request.buyerUserId() != null) return sellToUser(vehicle, request);
        if (request.buyerId() == null || request.installments() == null || request.installments().isEmpty() || request.transferOrDefault()) {
            throw new InvalidUserCounterpartyException("Alıcı ve ödeme planı seçin");
        }

        Contact buyer = getContact(request.buyerId(), vehicle.getTenantId());
        vehicle.setStatus(VehicleStatus.SOLD);
        vehicle.setSaleDate(request.saleDate());
        vehicle.setBuyer(buyer);
        vehicle.setSoldBy(getStaff(request.soldById(), vehicle.getTenantId()));
        vehicle.setSalePriceCents(request.salePriceCents());
        paymentPlanService.recordSale(vehicle, buyer, request);
        ledgerService.syncConsignmentSettlement(vehicle);   // konsinyede: BALANCE(sahibi) -> INCOME (satis - komisyon)
        coOwnershipService.onSale(vehicle, request.ownerReturns());   // ortak aracta kar paylari ve ortaga donus
        return toAdmin(vehicle, toDetail(vehicle));
    }

    @Override
    @Transactional
    public void remove(UUID id) {
        Vehicle vehicle = getVehicle(id);
        if (vehicle.getStatus() != VehicleStatus.IN_STOCK) {
            throw new VehicleNotRemovableException("Satılmış araç kaldırılamaz. Önce satışı geri alın.");
        }
        if (vehicle.getTransferFromVehicleId() != null) {
            throw new VehicleNotRemovableException("Bu araç devirle geldi. Kaldırmak için eski kaydın satışını geri alın.");
        }
        paymentPlanService.removePurchasePlan(vehicle);
        ledgerService.reverseVehicleEntries(vehicle.getId());   // alis, pesinat, masraflar, ortak katkilari: para geldigi yere doner
        ledgerService.reverseIfActive(vehicle.getPurchaseCapitalEntryId());   // kullanicidan alista sermayesine eklenen kisim
        vehicle.setPurchaseCapitalEntryId(null);
        vehicle.setPurchasePocketEntryId(null);   // cepten kisim araca bagli; yukarida terslendi, sermaye eski haline doner
        vehicle.setStatus(VehicleStatus.REMOVED);
    }

    @Override
    @Transactional
    public VehicleDetailAdmin undoSale(UUID id) {
        Vehicle vehicle = getVehicle(id);
        // Toplu satista sepetin butun araclari birlikte geri alinir
        List<UUID> basket = paymentPlanService.basketVehicleIds(vehicle.getId());
        List<Vehicle> all = basket.isEmpty() ? List.of(vehicle) : basket.stream().map(this::getVehicle).toList();
        for (Vehicle v : all) {
            if (v.getStatus() != VehicleStatus.SOLD) throw new VehicleNotSoldException();
            // Ayni plakayla stokta baska arac varsa geri donus tekillik kisitini bozar (devirle acilan kopya geri alinca kalkar)
            boolean transferCopyInStock = vehicleRepository.findByTransferFromVehicleId(v.getId())
                    .filter(t -> t.getStatus() == VehicleStatus.IN_STOCK).isPresent();
            if (!transferCopyInStock && vehicleRepository.existsByTenantIdAndPlateAndStatus(v.getTenantId(), v.getPlate(), VehicleStatus.IN_STOCK)) {
                throw new PlateAlreadyInStockException();
            }
        }
        for (Vehicle v : all) coOwnershipService.onUndoSale(v);   // ortaga nakit donus yapildiysa 1043; kar paylari terslenir
        if (vehicle.getBuyerUser() != null) undoUserSale(vehicle);
        paymentPlanService.undoSale(vehicle);
        for (Vehicle v : all) {
            v.setStatus(VehicleStatus.IN_STOCK);
            v.setSaleDate(null);
            v.setBuyer(null);
            v.setBuyerUser(null);
            v.setSoldBy(null);
            v.setSalePriceCents(null);
            ledgerService.syncConsignmentSettlement(v);   // stoktaki araca sahibine borc kalmaz
            coOwnershipService.syncContributions(v);      // katkilar yeniden araca baglanir: nakit sermaye yetmeli
        }
        return toAdmin(vehicle, toDetail(vehicle));
    }

    @Override
    @Transactional
    public VehicleDetailAdmin sellBasket(BasketSaleRequest r) {
        UUID tenantId = CurrentUser.tenantId();
        List<UUID> ids = r.items().stream().map(BasketSaleRequest.Item::vehicleId).toList();
        if (ids.size() < 2 || new HashSet<>(ids).size() != ids.size()) throw new InvalidBasketException();
        List<Vehicle> vehicles = ids.stream().map(this::getVehicle).toList();
        for (Vehicle v : vehicles) {
            if (v.getStatus() != VehicleStatus.IN_STOCK) throw new VehicleAlreadySoldException();
            if (r.saleDate().isBefore(v.getPurchaseDate())) throw new InvalidDateException();
        }
        Contact buyer = getContact(r.buyerId(), tenantId);
        PanelUser soldBy = getStaff(r.soldById(), tenantId);
        List<Long> prices = r.items().stream().map(BasketSaleRequest.Item::salePriceCents).toList();
        for (int k = 0; k < vehicles.size(); k++) {
            Vehicle v = vehicles.get(k);
            v.setStatus(VehicleStatus.SOLD);
            v.setSaleDate(r.saleDate());
            v.setBuyer(buyer);
            v.setSoldBy(soldBy);
            v.setSalePriceCents(prices.get(k));
        }
        paymentPlanService.recordBasketSale(vehicles, prices, buyer, r);
        for (Vehicle v : vehicles) {
            ledgerService.syncConsignmentSettlement(v);
            coOwnershipService.onSale(v, List.of());   // ortak aracta kar paylari sermayede kalir
        }
        return toAdmin(vehicles.get(0), toDetail(vehicles.get(0)));
    }

    // ------------------------------------------------------------------ yardimcilar

    private void apply(Vehicle vehicle, VehicleRequest r) {
        applyMakeModel(vehicle, r);
        vehicle.setModelYear(r.modelYear());
        vehicle.setFuel(r.fuel());
        vehicle.setTransmission(r.transmission());
        vehicle.setMileageKm(r.mileageKm());
        vehicle.setColor(NameUtil.normalize(blankToNull(r.color())));
        vehicle.setTramerCents(r.tramerCents());
        vehicle.setNotes(blankToNull(r.notes()));
        vehicle.setPurchaseDate(r.purchaseDate());
        vehicle.setBoughtBy(getStaff(r.boughtById(), vehicle.getTenantId()));

        Ownership ownership = r.ownershipOrDefault();
        vehicle.setOwnership(ownership);
        if (ownership == Ownership.CONSIGNMENT) {
            CommissionType type = r.commissionType();
            if (r.consignorId() == null || type == null
                    || (type == CommissionType.RATE && r.commissionRateBp() == null)
                    || (type == CommissionType.FIXED && r.commissionCents() == null)) {
                throw new ConsignmentDetailsRequiredException();
            }
            // Saticisi yok, sahibi (sermayedar) var. Istege bagli maliyet: sahibinin araci kaca aldigi (Kasim 2026); doluysa
            // kasadan araca yazilir ve sahibinin araclarina baglanir, bos ise arac eskisi gibi deftere girmez.
            vehicle.setConsignor(getConsignor(r.consignorId(), vehicle.getTenantId()));
            vehicle.setCommissionType(type);
            vehicle.setCommissionRateBp(type == CommissionType.RATE ? r.commissionRateBp() : null);
            vehicle.setCommissionCents(type == CommissionType.FIXED ? r.commissionCents() : null);
            vehicle.setSeller(null);
            vehicle.setSellerUser(null);
            vehicle.setActualPurchaseCents(r.actualPurchaseCents() == null || r.actualPurchaseCents() == 0 ? null : r.actualPurchaseCents());
            vehicle.setOfficialPurchaseCents(null);
        } else {
            vehicle.setConsignor(null);
            vehicle.setCommissionType(null);
            vehicle.setCommissionRateBp(null);
            vehicle.setCommissionCents(null);
            if (r.sellerId() != null && r.sellerUserId() != null) throw new InvalidUserCounterpartyException("Satıcı olarak ya kişi ya kullanıcı seçin");
            vehicle.setSeller(getContact(r.sellerId(), vehicle.getTenantId()));
            vehicle.setSellerUser(r.sellerUserId() == null ? null : getUser(r.sellerUserId(), vehicle.getTenantId()));
            vehicle.setActualPurchaseCents(r.actualPurchaseCents());
            vehicle.setOfficialPurchaseCents(r.officialPurchaseCents());
        }
        applyShopCut(vehicle, r);
    }

    /** Dukkan payi sadece ortak aracta, istege bagli: oranda baz puan, sabitte tutar. */
    private static void applyShopCut(Vehicle vehicle, VehicleRequest r) {
        CommissionType type = r.shopCutType();
        if (type != null && (vehicle.getOwnership() != Ownership.CO_OWNED
                || (type == CommissionType.RATE && r.shopCutRateBp() == null)
                || (type == CommissionType.FIXED && r.shopCutCents() == null))) {
            throw new InvalidShopCutException();
        }
        vehicle.setShopCutType(type);
        vehicle.setShopCutRateBp(type == CommissionType.RATE ? r.shopCutRateBp() : null);
        vehicle.setShopCutCents(type == CommissionType.FIXED ? r.shopCutCents() : null);
    }

    /**
     * Marka / model katalogdan: yeni kayitta ya da marka / model degistiyse katalogda olmali (katalog yazimina cevrilir,
     * paket korunur). Katalog oncesi eski kayit marka / modeline dokunulmadikca aynen kalir. "Listede yok" onaylandiysa
     * once galerinin katalogune eklenir.
     */
    private void applyMakeModel(Vehicle vehicle, VehicleRequest r) {
        String make = r.make().trim();
        String model = r.model().trim().replaceAll("\\s+", " ");
        boolean unchanged = vehicle.getId() != null && make.equalsIgnoreCase(vehicle.getMake()) && model.equals(vehicle.getModel());
        if (unchanged) return;
        if (Boolean.TRUE.equals(r.addToCatalog())) {
            String base = r.catalogModel() == null || r.catalogModel().isBlank() ? model : r.catalogModel();
            catalogService.add(vehicle.getTenantId(), make, base);
        }
        CatalogService.Resolved resolved = catalogService.resolve(vehicle.getTenantId(), make, model)
                .orElseThrow(CatalogUnknownException::new);
        vehicle.setMake(resolved.make());
        vehicle.setModel(resolved.model());
    }

    /**
     * Konsinye arac: maliyet girildiyse kasadan (ya da secilen hesaptan) araca pesin alis olarak yazilir (planli alis yok);
     * maliyet bossa alis deftere yazilmaz, varsa onceki alis hareketi / plani kaldirilir.
     */
    private void syncPurchase(Vehicle vehicle, VehicleRequest r) {
        if (vehicle.getOwnership() == Ownership.CONSIGNMENT) {
            if (r.purchasePlan() != null && !r.purchasePlan().isEmpty()) throw new ConsignmentDetailsRequiredException();
            UUID account = vehicle.getActualPurchaseCents() == null ? null
                    : r.purchaseAccountId() != null ? r.purchaseAccountId() : ledgerService.cashAccountId();
            paymentPlanService.syncPurchase(vehicle, account, null);
        } else {
            paymentPlanService.syncPurchase(vehicle, r.purchaseAccountId(), r.purchasePlan());
        }
    }

    /** Konsinye aracin sahibinin parasini etkileyen alanlar (sahiplik, sahibi, maliyet, tarih, cepten kismin hareketi). */
    private static List<Object> consignmentFunding(Vehicle v, UUID capitalEntryId) {
        return java.util.Arrays.asList(v.getOwnership(), v.getConsignor() == null ? null : v.getConsignor().getId(),
                v.getActualPurchaseCents(), v.getPurchaseDate(), capitalEntryId);
    }

    // ------------------------------------------------------------------ cepten alis (V31)

    /**
     * Dukkan sahibi araci (kismen ya da tamamen) cebinden aldi: o kisim kasadan / bankadan cikmaz, sermayesine eklenir.
     * Sadece dukkan sahibi, galeri araci, pesin ve kisiden alista. Tutar syncPurchase'tan once araca yazilir; kasa / banka
     * hareketi kalan kadar olur.
     */
    private void preparePocket(Vehicle vehicle, VehicleRequest r) {
        long pocket = r.pocketCents() == null ? 0 : r.pocketCents();
        if (pocket > 0) {
            if (!CurrentUser.isAdmin()) throw new PocketPurchaseNotAllowedException("Cepten alışı sadece dükkân sahibi girer");
            if (vehicle.getOwnership() != Ownership.DEALER) {
                throw new PocketPurchaseNotAllowedException("Cepten alış sadece galeri aracında seçilir");
            }
            if (r.purchasePlan() != null && !r.purchasePlan().isEmpty()) {
                throw new PocketPurchaseNotAllowedException("Cepten alışta ödeme planı ya da çek / senet kullanılmaz");
            }
            if (vehicle.getSellerUser() != null) throw new PocketPurchaseNotAllowedException("Kullanıcıdan alışta cepten seçilmez");
            long price = vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents();
            if (pocket > price) throw new PocketPurchaseNotAllowedException("Cepten kısım alış bedelini aşamaz");
        }
        vehicle.setPurchasePocketCents(pocket);
    }

    /** Cepten kismin hareketi: ilk yazan dukkan sahibinin sermayesine; duzenlemede ayni kiside kalir. */
    private void syncPocket(Vehicle vehicle) {
        UUID capitalAccount = null;
        if (vehicle.getPurchasePocketCents() > 0) {
            capitalAccount = ledgerService.pocketCapitalAccountId(vehicle);
            if (capitalAccount == null) capitalAccount = capitalService.capitalAccountId(CurrentUser.principal().userId());
        }
        ledgerService.syncPocketPurchase(vehicle, capitalAccount);
    }

    // ------------------------------------------------------------------ panel kullanicisiyla alis / satis (V25)

    /**
     * Kullanicidan alis: alis kasadan tam bedelle yazilir (katki ve maliyet her zamanki gibi), bedelin sellerCapitalCents kadari ayni
     * hesaba onun sermaye girisi olarak geri yazilir. Net: kasadan nakit kismi cikar, kullanicinin sermayesi sermaye kismi kadar artar.
     */
    private void syncSellerCapital(Vehicle vehicle, VehicleRequest r) {
        long capital = r.sellerCapitalCents() == null ? 0 : r.sellerCapitalCents();
        // Konsinye aracta "cepten" kisim: sahibi aracin bu kismini kendi cebinden almis; ayni yolla onun sermayesine eklenir.
        boolean consignment = vehicle.getOwnership() == Ownership.CONSIGNMENT;
        PanelUser seller = consignment ? vehicle.getConsignor() : vehicle.getSellerUser();
        if (!consignment && seller != null && r.purchasePlan() != null && !r.purchasePlan().isEmpty()) {
            throw new InvalidUserCounterpartyException("Kullanıcıdan alışta ödeme planı kurulmaz; nakit ve sermaye kısmı girin");
        }
        if (capital > 0) {
            if (seller == null) throw new InvalidUserCounterpartyException("Sermayeye eklenecek kısım sadece kullanıcıdan alışta girilir");
            if (seller.isOwner()) throw new InvalidUserCounterpartyException("Dükkân sahibinin parası havuzdadır; sermaye kısmı girilmez");
            long price = vehicle.getActualPurchaseCents() == null ? 0 : vehicle.getActualPurchaseCents();
            if (capital > price) throw new InvalidUserCounterpartyException("Sermaye kısmı alış bedelini aşamaz");
        }
        UUID account = r.purchaseAccountId() != null ? r.purchaseAccountId() : ledgerService.cashAccountId();
        // Ayni kullanici, tutar, tarih ve hesap ise dokunma; degilse eskiyi tersle, yenisini yaz
        UUID existing = vehicle.getPurchaseCapitalEntryId();
        LedgerService.CapitalEntryInfo ref = existing == null ? null : ledgerService.capitalEntryInfo(existing).orElse(null);
        boolean same = ref != null && capital > 0 && ref.active() && ref.userId().equals(seller.getId()) && ref.amountCents() == capital
                && ref.date().equals(vehicle.getPurchaseDate()) && ref.moneyAccountId().equals(account);
        if (same || (existing == null && capital == 0)) return;
        ledgerService.reverseIfActive(existing);
        vehicle.setPurchaseCapitalEntryId(capital == 0 ? null : capitalService.move(seller.getId(), new CapitalRequests.Movement(
                CapitalRequests.Direction.IN, account, vehicle.getPurchaseDate(), capital,
                vehicle.getPlate() + " alışı: bedel sermayeye")).id());
    }

    /**
     * Panel kullanicisina satis. Bedel taksitsiz: satis hareketi INCOME -> kasa / banka (satis gununde tahsil edilmis; ortakli ve konsinye
     * aracta paylar hemen dagitilir). Alan sermayedarsa:
     * - "satmak icin al" (devir): nakit kismi onun sermaye girisi olarak kasaya yazilir, arac ayni bilgilerle onun stoguna yeni kayitla
     *   girer (maliyeti satis bedeli, %100 onun, sermayesinden). Net: sermayesinden sermaye kismi kadar duser, arac varliklarina eklenir.
     * - devirsiz: sermaye kismi kasadan onun sermayesine cekis olarak duser (nakit kismi kasada kalir).
     * Alan dukkan sahibiyse sadece devir olur ve bedelin tamami havuzdan (sermaye kismi yok).
     */
    private VehicleDetailAdmin sellToUser(Vehicle vehicle, VehicleSaleRequest r) {
        UUID tenantId = vehicle.getTenantId();
        PanelUser buyer = getUser(r.buyerUserId(), tenantId);
        boolean transfer = r.transferOrDefault();
        long price = r.salePriceCents();
        long capital = r.buyerCapitalCents() == null ? 0 : r.buyerCapitalCents();
        if (r.buyerId() != null || (r.installments() != null && !r.installments().isEmpty())) {
            throw new InvalidUserCounterpartyException("Kullanıcıya satışta ödeme planı kurulmaz; sermaye ve nakit kısmı girin");
        }
        if (!buyer.isOwner() && !buyer.isCapitalHolder()) throw new InvalidUserCounterpartyException("Çalışana satış yapılamaz");
        if (capital > price) throw new InvalidUserCounterpartyException("Sermaye kısmı satış bedelini aşamaz");
        if (buyer.isOwner() && (capital > 0 || !transfer)) {
            throw new InvalidUserCounterpartyException("Dükkân sahibine satış sadece \"satmak için al\" ile olur; bedel havuzdan ödenir");
        }
        if (ownsAlone(vehicle, buyer)) throw new InvalidUserCounterpartyException("Araç zaten bu kullanıcının");
        UUID cash = r.cashAccountId() != null ? r.cashAccountId() : ledgerService.cashAccountId();

        vehicle.setStatus(VehicleStatus.SOLD);
        vehicle.setSaleDate(r.saleDate());
        vehicle.setBuyer(null);
        vehicle.setBuyerUser(buyer);
        vehicle.setSoldBy(getStaff(r.soldById(), tenantId));
        vehicle.setSalePriceCents(price);
        ledgerService.writeDirectSale(vehicle, cash, price, r.saleDate(),
                (transfer ? "Devir: " : "Satış: ") + buyer.getFullName());
        // Satan tarafin paylari once dagitilir (alan ayni aracin ortagiysa onun payi da sermayesine gecer)
        ledgerService.syncConsignmentSettlement(vehicle);
        coOwnershipService.onSale(vehicle, r.ownerReturns());
        if (transfer) {
            long cashPart = price - capital;
            if (!buyer.isOwner() && cashPart > 0) {
                vehicle.setSaleCapitalEntryId(capitalService.move(buyer.getId(), new CapitalRequests.Movement(CapitalRequests.Direction.IN,
                        cash, r.saleDate(), cashPart, vehicle.getPlate() + " devri: nakit kısmı")).id());
            }
            openTransferVehicle(vehicle, buyer, price, r.saleDate(), cash);
        } else if (capital > 0) {
            vehicle.setSaleCapitalEntryId(capitalService.move(buyer.getId(), new CapitalRequests.Movement(CapitalRequests.Direction.OUT,
                    cash, r.saleDate(), capital, vehicle.getPlate() + " alışı: sermayeden")).id());
        }
        return toAdmin(vehicle, toDetail(vehicle));
    }

    /** Arac zaten tek basina bu kullanicinin mi (galeri araci dukkan sahibinin, konsinye sahibinin, ortakli tek sahibin). */
    private boolean ownsAlone(Vehicle v, PanelUser user) {
        return switch (v.getOwnership()) {
            case DEALER -> user.isOwner();
            case CONSIGNMENT -> v.getConsignor() != null && v.getConsignor().getId().equals(user.getId());
            case CO_OWNED -> {
                var owners = coOwnershipService.owners(v);
                yield owners.size() == 1 && user.getId().equals(owners.get(0).userId());
            }
        };
    }

    /** "Satmak icin al": alanin stoguna ayni bilgilerle yeni kayit; alis kasadan satis bedeliyle, sermayedarsa %100 onun (sermayeden). */
    private void openTransferVehicle(Vehicle sold, PanelUser buyer, long price, LocalDate date, UUID cash) {
        Vehicle t = new Vehicle();
        t.setTenantId(sold.getTenantId());
        t.setPlate(sold.getPlate());
        t.setMake(sold.getMake());
        t.setModel(sold.getModel());
        t.setModelYear(sold.getModelYear());
        t.setFuel(sold.getFuel());
        t.setTransmission(sold.getTransmission());
        t.setMileageKm(sold.getMileageKm());
        t.setColor(sold.getColor());
        t.setTramerCents(sold.getTramerCents());
        t.setNotes(sold.getNotes());
        t.setPurchaseDate(date);
        t.setBoughtBy(sold.getSoldBy());
        t.setActualPurchaseCents(price);
        t.setOfficialPurchaseCents(price);
        t.setTransferFromVehicleId(sold.getId());
        t.setOwnership(buyer.isOwner() ? Ownership.DEALER : Ownership.CO_OWNED);
        Vehicle saved = vehicleRepository.save(t);
        for (VehiclePart p : vehiclePartRepository.findByVehicleId(sold.getId())) {
            VehiclePart copy = new VehiclePart();
            copy.setTenantId(saved.getTenantId());
            copy.setVehicleId(saved.getId());
            copy.setPart(p.getPart());
            copy.setCondition(p.getCondition());
            vehiclePartRepository.save(copy);
        }
        coOwnershipService.syncOwners(saved, buyer.isOwner() ? null : List.of(new ShareRequest(buyer.getId(), 10000)));
        ledgerService.syncVehiclePurchase(saved, cash);
        coOwnershipService.syncContributions(saved);   // sermayedarin nakitteki sermayesi bedeli karsilamali
    }

    /** Kullaniciya satisin geri alinmasi: devirle acilan kayit (stoktaysa) kaldirilir, sermaye hareketi terslenir. */
    private void undoUserSale(Vehicle vehicle) {
        vehicleRepository.findByTransferFromVehicleId(vehicle.getId()).ifPresent(t -> {
            if (t.getStatus() != VehicleStatus.IN_STOCK) {
                throw new TransferLockedException("Devirle alınan araç satılmış; önce onun satışını geri alın.");
            }
            paymentPlanService.removePurchasePlan(t);
            ledgerService.reverseVehicleEntries(t.getId());
            t.setStatus(VehicleStatus.REMOVED);
            t.setTransferFromVehicleId(null);
            vehicleRepository.saveAndFlush(t);
        });
        ledgerService.reverseIfActive(vehicle.getSaleCapitalEntryId());
        vehicle.setSaleCapitalEntryId(null);
    }

    /** Devirle acilan kayitta alis bilgisi devirden gelir. */
    private static boolean transferPurchaseChanged(Vehicle v, VehicleRequest r) {
        return !java.util.Objects.equals(v.getActualPurchaseCents(), r.actualPurchaseCents())
                || !v.getPurchaseDate().equals(r.purchaseDate())
                || v.getOwnership() != r.ownershipOrDefault()
                || r.sellerId() != null || r.sellerUserId() != null
                || (r.purchasePlan() != null && !r.purchasePlan().isEmpty());
    }

    private PanelUser getUser(UUID id, UUID tenantId) {
        PanelUser u = panelUserRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (u.getTenant() == null || !u.getTenant().getId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        return u;
    }

    private Vehicle getVehicle(UUID id) {
        Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!vehicle.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (vehicle.getStatus() == VehicleStatus.REMOVED) throw new RecordNotFoundException();
        return vehicle;
    }

    private Contact getContact(UUID id, UUID tenantId) {
        if (id == null) return null;
        Contact contact = contactRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!contact.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        return contact;
    }

    private PanelUser getStaff(UUID id, UUID tenantId) {
        if (id == null) return null;
        PanelUser staff = panelUserRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (staff.getTenant() == null || !staff.getTenant().getId().equals(tenantId)) {
            throw new SourceNotBelongToTenantException();
        }
        return staff;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(DEALER_ZONE));
    }

    /** Stokta ise bugune kadar, satildiysa satis gunune kadar gecen gun. */
    static long daysInStock(Vehicle vehicle, LocalDate today) {
        LocalDate end = vehicle.getSaleDate() != null ? vehicle.getSaleDate() : today;
        return Math.max(0, ChronoUnit.DAYS.between(vehicle.getPurchaseDate(), end));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private VehicleSummary toSummary(Vehicle v, List<VehiclePart> parts, LocalDate today, boolean admin) {
        int locallyPainted = 0, painted = 0, replaced = 0;
        for (VehiclePart p : parts) {
            switch (p.getCondition()) {
                case LOCALLY_PAINTED -> locallyPainted++;
                case PAINTED -> painted++;
                case REPLACED -> replaced++;
                case ORIGINAL -> { }
            }
        }
        return new VehicleSummary(v.getId(), v.getPlate(), v.getMake(), v.getModel(), v.getModelYear(), v.getMileageKm(),
                v.getFuel(), v.getTransmission(), v.getColor(), v.getStatus(), v.getPurchaseDate(), v.getSaleDate(),
                daysInStock(v, today), new DamageSummary(locallyPainted, painted, replaced),
                admin ? contactSummary(v.getBuyer()) : null, v.getOwnership());
    }

    private VehicleDetail toDetail(Vehicle v) {
        Map<BodyPart, PartCondition> parts = new EnumMap<>(BodyPart.class);
        for (BodyPart p : BodyPart.values()) parts.put(p, PartCondition.ORIGINAL);
        if (v.getId() != null) {
            vehiclePartRepository.findByVehicleId(v.getId()).forEach(p -> parts.put(p.getPart(), p.getCondition()));
        }
        return new VehicleDetail(v.getId(), v.getPlate(), v.getMake(), v.getModel(), v.getModelYear(), v.getMileageKm(),
                v.getFuel(), v.getTransmission(), v.getColor(), v.getTramerCents(), v.getNotes(), v.getStatus(),
                v.getPurchaseDate(), v.getSaleDate(), daysInStock(v, today()), parts, v.getOwnership());
    }

    private VehicleDetailAdmin toAdmin(Vehicle v, VehicleDetail detail) {
        return new VehicleDetailAdmin(detail,
                contactSummary(v.getSeller()), staffSummary(v.getBoughtBy()), v.getActualPurchaseCents(), v.getOfficialPurchaseCents(),
                contactSummary(v.getBuyer()), staffSummary(v.getSoldBy()), v.getSalePriceCents(),
                ledgerService.purchaseAccountId(v.getId()),
                userSummary(v.getConsignor()), v.getCommissionType(), v.getCommissionRateBp(), v.getCommissionCents(),
                coOwnershipService.owners(v), v.getShopCutType(), v.getShopCutRateBp(), v.getShopCutCents(),
                userSummary(v.getSellerUser()),
                v.getPurchaseCapitalEntryId() == null ? null : ledgerService.capitalEntryInfo(v.getPurchaseCapitalEntryId())
                        .filter(LedgerService.CapitalEntryInfo::active).map(LedgerService.CapitalEntryInfo::amountCents).orElse(null),
                userSummary(v.getBuyerUser()), v.getTransferFromVehicleId(),
                vehicleRepository.findByTransferFromVehicleId(v.getId()).map(Vehicle::getId).orElse(null),
                v.getPurchasePocketCents());
    }

    private static ContactSummary userSummary(PanelUser u) {
        return u == null ? null : new ContactSummary(u.getId(), u.getFullName(), u.getPhone());
    }

    /** Konsinye sahibi: sermayedar kullanici (V22). */
    private PanelUser getConsignor(UUID id, UUID tenantId) {
        PanelUser u = panelUserRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (u.getTenant() == null || !u.getTenant().getId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        if (!u.isCapitalHolder() || u.isOwner()) throw new ConsignmentDetailsRequiredException();
        return u;
    }

    private static ContactSummary contactSummary(Contact c) {
        return c == null ? null : new ContactSummary(c.getId(), c.getName(), c.getPhone());
    }

    private static StaffSummary staffSummary(PanelUser u) {
        return u == null ? null : new StaffSummary(u.getId(), u.getFullName());
    }
}
