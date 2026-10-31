package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CapitalExceededException;
import com.galeri.app.exception.runtime.InvalidPeriodException;
import com.galeri.app.exception.runtime.NotAPartnerException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.CommissionType;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.response.CapitalResponses.Partner;
import com.galeri.app.model.response.CapitalResponses.PartnerProfit;
import com.galeri.app.model.response.CapitalResponses.PeriodProfit;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapitalServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private InstallmentRepository installmentRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private PanelUserRepository panelUserRepository;
    @Mock private CoOwnershipService coOwnershipService;

    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final List<Account> capitalAccounts = new ArrayList<>();
    private final List<LedgerEntry> capitalEntries = new ArrayList<>();
    private CapitalServiceImpl service;
    private Account cash;
    private Account income;

    @BeforeEach
    void setUp() {
        service = new CapitalServiceImpl(accountRepository, entryRepository, installmentRepository, vehicleRepository, panelUserRepository,
                coOwnershipService, Clock.fixed(Instant.parse("2026-10-01T06:00:00Z"), ZoneOffset.UTC));
        TestSession.open(userId, tenantId, "ADMIN");
        PanelUser me = new PanelUser();
        me.setId(userId);
        when(panelUserRepository.getReferenceById(userId)).thenReturn(me);
        cash = account(AccountType.CASH, null);
        income = account(AccountType.INCOME, null);
        when(accountRepository.findById(cash.getId())).thenReturn(Optional.of(cash));
        when(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.INCOME)).thenReturn(Optional.of(income));
        when(accountRepository.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenantId, List.of(AccountType.CAPITAL))).thenReturn(capitalAccounts);
        when(entryRepository.touchingType(tenantId, AccountType.CAPITAL)).thenReturn(capitalEntries);
        when(panelUserRepository.findCapitalHolders(tenantId)).thenReturn(holders);
        when(panelUserRepository.findAllById(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return ids.stream().map(usersById::get).filter(java.util.Objects::nonNull).toList();
        });
        when(entryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> {
            LedgerEntry e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    // ------------------------------------------------------------------ donem kari

    @Test
    void donemKari_aracKariArtiKomisyonEksiGenelGider_ortakAractaSadeceHavuzPayiNeteGirer() {
        Vehicle dealer = sold(Ownership.DEALER, FROM, 130000000L, 100000000L);          // donemin ilk gunu: dahil
        Vehicle consigned = sold(Ownership.CONSIGNMENT, TO, 145000000L, null);           // donemin son gunu: dahil
        consigned.setCommissionType(CommissionType.FIXED);
        consigned.setCommissionCents(4000000L);
        Vehicle coOwned = sold(Ownership.CO_OWNED, LocalDate.of(2026, 8, 10), 110000000L, 98000000L);   // kar 120.000
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, FROM, TO)).thenReturn(List.of(coOwned, consigned, dealer));
        when(entryRepository.vehicleExpenseTotals(tenantId, LedgerEntryType.VEHICLE_EXPENSE, AccountType.VEHICLE))
                .thenReturn(List.<Object[]>of(new Object[]{dealer.getId(), 5000000L}));
        // Ortaga satista yazilan kar payi 72.000 (%60); havuza 48.000 kalir
        when(entryRepository.partnerProfitShareTotals(tenantId, LedgerEntryType.VEHICLE_PROFIT_SHARE, AccountType.CAPITAL))
                .thenReturn(List.<Object[]>of(new Object[]{coOwned.getId(), 7200000L}));
        when(entryRepository.netInto(tenantId, AccountType.EXPENSE, FROM, TO)).thenReturn(8000000L);

        PeriodProfit p = service.periodProfit(FROM, TO);

        assertThat(p.vehicleProfitCents()).isEqualTo(29800000L);   // 1.300.000 - (1.000.000 + 50.000) + havuz payi 48.000
        assertThat(p.commissionCents()).isEqualTo(4000000L);
        assertThat(p.generalExpenseCents()).isEqualTo(8000000L);
        assertThat(p.netCents()).isEqualTo(25800000L);             // 298.000 + 40.000 - 80.000
        assertThat(p.coOwnedProfitCents()).isEqualTo(7200000L);    // ortaklarin payi: sermayelerine gecti, nete girmez
        assertThat(p.vehicles()).filteredOn(v -> v.ownership() == Ownership.CO_OWNED).singleElement()
                .satisfies(v -> assertThat(v.poolProfitCents()).isEqualTo(4800000L));
        assertThat(p.vehicles()).extracting(v -> v.saleDate()).containsExactly(FROM, LocalDate.of(2026, 8, 10), TO);
        verify(entryRepository).netInto(tenantId, AccountType.EXPENSE, FROM, TO);   // genel gider ayni (dahil) sinirlarla
    }

    @Test
    void donemKari_ortaklaraSermayePayiylaOnerilir_donemSonrasiSermayeSayilmaz() {
        Account furkan = partner("Furkan", true);
        Account selin = partner("Selin", false);
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, furkan, cash, 6000000L, LocalDate.of(2026, 1, 1)));
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, selin, cash, 4000000L, LocalDate.of(2026, 1, 1)));
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, selin, cash, 9000000L, TO.plusDays(1)));     // donemden sonra
        capitalEntries.add(entry(LedgerEntryType.PROFIT_ALLOCATION, furkan, income, 300000L, TO));       // donem icinde eklenen
        capitalEntries.add(entry(LedgerEntryType.PROFIT_ALLOCATION, selin, income, 100000L, FROM.minusDays(1)));   // onceki donem
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, FROM, TO)).thenReturn(List.of());
        when(entryRepository.netInto(tenantId, AccountType.EXPENSE, FROM, TO)).thenReturn(-1000000L);   // net kar 10.000 TL

        List<PartnerProfit> partners = service.periodProfit(FROM, TO).partners();

        // Pay icin sermaye donem sonunda, bu donemin kar payi haric: Furkan 60.000 (3.000 bu donemden eklendi, sayilmaz),
        // Selin 40.000 + 1.000 (onceki donemin kar payi sayilir). Donemden sonraki giris sayilmaz.
        assertThat(partners).extracting(PartnerProfit::name).containsExactly("Furkan", "Selin");
        assertThat(partners).extracting(PartnerProfit::capitalCents).containsExactly(6000000L, 4100000L);
        assertThat(partners).extracting(PartnerProfit::allocatedCents).containsExactly(300000L, 0L);
        assertThat(partners.stream().mapToLong(PartnerProfit::suggestedCents).sum()).isEqualTo(1000000L);
        assertThat(partners).extracting(PartnerProfit::suggestedCents).containsExactly(594060L, 405940L);
    }

    @Test
    void karPayiEklenince_donemOnerisiDegismez() {
        Account furkan = partner("Furkan", true);
        Account selin = partner("Selin", false);
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, furkan, cash, 6000000L, LocalDate.of(2026, 1, 1)));
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, selin, cash, 4000000L, LocalDate.of(2026, 1, 1)));
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, FROM, TO)).thenReturn(List.of());
        when(entryRepository.netInto(tenantId, AccountType.EXPENSE, FROM, TO)).thenReturn(-1000000L);
        List<Long> before = service.periodProfit(FROM, TO).partners().stream().map(PartnerProfit::suggestedCents).toList();

        capitalEntries.add(entry(LedgerEntryType.PROFIT_ALLOCATION, furkan, income, before.get(0), TO));
        List<PartnerProfit> after = service.periodProfit(FROM, TO).partners();

        assertThat(after).extracting(PartnerProfit::suggestedCents).containsExactlyElementsOf(before);
        assertThat(after.get(0).allocatedCents()).isEqualTo(before.get(0));
    }

    @Test
    void donemBaslangiciBitistenSonraysa_reddedilir() {
        assertThatThrownBy(() -> service.periodProfit(TO, FROM)).isInstanceOf(InvalidPeriodException.class);
    }

    // ------------------------------------------------------------------ paylar

    @Test
    void paylar_sermayeHareketlerindenHesaplanir_cekisVeDuzeltmeDuser() {
        Account furkan = partner("Furkan", true);
        Account kerem = partner("Kerem", false);
        LedgerEntry in = entry(LedgerEntryType.CAPITAL, furkan, cash, 8000000L, FROM);
        capitalEntries.add(in);
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, cash, furkan, 1500000L, FROM.plusDays(5)));   // cekis
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, kerem, cash, 2000000L, FROM));
        LedgerEntry wrong = entry(LedgerEntryType.CAPITAL, kerem, cash, 999L, FROM);
        capitalEntries.add(wrong);
        LedgerEntry reversal = entry(LedgerEntryType.CAPITAL, cash, kerem, 999L, FROM);
        reversal.setCorrectedEntryId(wrong.getId());
        capitalEntries.add(reversal);
        capitalEntries.add(entry(LedgerEntryType.PROFIT_ALLOCATION, kerem, income, 1500000L, FROM));   // kar payi sermayeyi artirir

        List<Partner> partners = service.partners();

        assertThat(partners).extracting(Partner::name).containsExactly("Furkan", "Kerem");   // dukkan sahibi once
        assertThat(partners).extracting(Partner::capitalCents).containsExactly(6500000L, 3500000L);
        assertThat(partners).extracting(Partner::shareBp).containsExactly(6500, 3500);
    }

    @Test
    void sermayeCekisi_sermayedenFazlaysa_reddedilir() {
        Account selin = partner("Selin", false);
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, selin, cash, 100000L, FROM));

        assertThatThrownBy(() -> service.move(selin.getUserId(),
                new CapitalRequests.Movement(CapitalRequests.Direction.OUT, cash.getId(), FROM, 100001L, null)))
                .isInstanceOf(CapitalExceededException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void sermayeCekisi_araclardaBagliKisimCekilemez_nakittekiKadar() {
        Account selin = partner("Selin", false);
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, selin, cash, 100000L, FROM));
        when(coOwnershipService.boundCapital(eq(tenantId), any())).thenReturn(Map.of(selin.getUserId(), 60000L));

        assertThatThrownBy(() -> service.move(selin.getUserId(),
                new CapitalRequests.Movement(CapitalRequests.Direction.OUT, cash.getId(), FROM, 40001L, null)))
                .isInstanceOf(CapitalExceededException.class);
        Partner p = service.partners().getFirst();
        assertThat(p.capitalCents()).isEqualTo(100000L);
        assertThat(p.cashCents()).isEqualTo(40000L);
        assertThat(p.boundCents()).isEqualTo(60000L);
    }

    @Test
    void donemKariAgirligi_araclardaBagliSermayeyiDislar() {
        Account furkan = partner("Furkan", true);
        Account burak = partner("Burak", false);
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, furkan, cash, 1000000L, FROM));
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, burak, cash, 1000000L, FROM));
        when(coOwnershipService.boundCapital(tenantId, TO)).thenReturn(Map.of(burak.getUserId(), 500000L));   // yarisi aracta
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, FROM, TO)).thenReturn(List.of());
        when(entryRepository.netInto(tenantId, AccountType.EXPENSE, FROM, TO)).thenReturn(-300000L);   // net 3.000 TL

        List<PartnerProfit> partners = service.periodProfit(FROM, TO).partners();

        assertThat(partners).extracting(PartnerProfit::capitalCents).containsExactly(1000000L, 500000L);
        assertThat(partners).extracting(PartnerProfit::suggestedCents).containsExactly(200000L, 100000L);
    }

    @Test
    void sermayeGirisi_sermayeHesabindanKasaya_yeniOrtakIcinHesapAcilir() {
        PanelUser c = user("Yeni Ortak", "INVESTOR");
        when(accountRepository.findFirstByTenantIdAndTypeAndUserId(tenantId, AccountType.CAPITAL, c.getId())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        service.move(c.getId(), new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash.getId(), FROM, 5000000L, null));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceAccount().getType()).isEqualTo(AccountType.CAPITAL);
        assertThat(saved.getValue().getSourceAccount().getUserId()).isEqualTo(c.getId());
        assertThat(saved.getValue().getTargetAccount()).isSameAs(cash);
        assertThat(saved.getValue().getDescription()).isEqualTo("Sermaye girişi");
    }

    // ------------------------------------------------------------------ kar payi

    @Test
    void karPayi_sermayeyeEklenir_gelirdenOrtaginSermayesine() {
        Account furkan = partner("Furkan", true);
        capitalEntries.add(entry(LedgerEntryType.CAPITAL, furkan, cash, 1000000L, FROM));

        service.allocate(new CapitalRequests.Allocation(furkan.getUserId(), TO, 250000L, FROM, TO));

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entryRepository).save(saved.capture());
        LedgerEntry e = saved.getValue();
        assertThat(e.getType()).isEqualTo(LedgerEntryType.PROFIT_ALLOCATION);
        assertThat(e.getDescription()).isEqualTo("Kâr payı sermayeye: 01.07.2026 – 30.09.2026");
        capitalEntries.add(e);
        assertThat(CapitalServiceImpl.capitalByAccount(capitalEntries, null)).containsEntry(furkan.getId(), 1250000L);
        assertThat(CapitalServiceImpl.capitalByAccount(capitalEntries, TO.minusDays(1))).containsEntry(furkan.getId(), 1000000L);
    }

    @Test
    void ortakOlmayanaKarPayi_reddedilir() {
        PanelUser investor = user("Ali", "INVESTOR");
        when(accountRepository.findFirstByTenantIdAndTypeAndUserId(tenantId, AccountType.CAPITAL, investor.getId())).thenReturn(Optional.empty());
        PanelUser staff = user("Çalışan", "STAFF");

        assertThatThrownBy(() -> service.allocate(new CapitalRequests.Allocation(investor.getId(), TO, 1L, FROM, TO)))
                .isInstanceOf(NotAPartnerException.class);
        assertThatThrownBy(() -> service.move(staff.getId(),
                new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash.getId(), FROM, 1L, null)))
                .isInstanceOf(NotAPartnerException.class);   // calisanin sermayesi olmaz
    }

    // ------------------------------------------------------------------ yardimcilar

    private final Map<UUID, PanelUser> usersById = new java.util.HashMap<>();
    private final List<PanelUser> holders = new ArrayList<>();

    private PanelUser user(String name, String role) {
        Tenant t = new Tenant();
        t.setId(tenantId);
        PanelUser u = new PanelUser();
        u.setId(UUID.randomUUID());
        u.setTenant(t);
        u.setFullName(name);
        u.setRole(role);
        usersById.put(u.getId(), u);
        if (u.isCapitalHolder()) holders.add(u);
        when(panelUserRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private Account partner(String name, boolean owner) {
        PanelUser u = user(name, owner ? "ADMIN" : "INVESTOR");
        Account a = account(AccountType.CAPITAL, u.getId());
        a.setName(name);
        capitalAccounts.add(a);
        when(accountRepository.findFirstByTenantIdAndTypeAndUserId(tenantId, AccountType.CAPITAL, u.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private Account account(AccountType type, UUID userId) {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setType(type);
        a.setName(type.name());
        a.setUserId(userId);
        return a;
    }

    private LedgerEntry entry(LedgerEntryType type, Account source, Account target, long amount, LocalDate date) {
        LedgerEntry e = new LedgerEntry();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setType(type);
        e.setSourceAccount(source);
        e.setTargetAccount(target);
        e.setAmountCents(amount);
        e.setEntryDate(date);
        return e;
    }

    private Vehicle sold(Ownership ownership, LocalDate saleDate, long price, Long purchase) {
        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID());
        v.setTenantId(tenantId);
        v.setPlate("34 AB " + (int) (Math.random() * 900 + 100));
        v.setMake("Renault");
        v.setModel("Clio");
        v.setOwnership(ownership);
        v.setStatus(VehicleStatus.SOLD);
        v.setPurchaseDate(saleDate.minusDays(30));
        v.setSaleDate(saleDate);
        v.setSalePriceCents(price);
        v.setActualPurchaseCents(purchase);
        return v;
    }
}
