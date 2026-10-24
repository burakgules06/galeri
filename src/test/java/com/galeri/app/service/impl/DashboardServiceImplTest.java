package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.response.DashboardResponse;
import com.galeri.app.model.response.DashboardResponse.MonthStat;
import com.galeri.app.model.response.DashboardResponse.PartnerShare;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private VehicleRepository vehicleRepository;

    // "Su an": 1 Temmuz 2026, Istanbul
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T06:00:00Z"), ZoneOffset.UTC);
    private final UUID tenantId = UUID.randomUUID();
    private DashboardServiceImpl service;

    private final Contact owner = contact("Furkan Kaya", true);
    private final Contact partner = contact("Selin Arslan", false);
    private final Contact buyer = contact("Ayse Demir", false);
    private final Contact seller = contact("Mehmet Yilmaz", false);

    private final Account cash = account(AccountType.CASH, null);
    private final Account ownerCapital = account(AccountType.CAPITAL, owner);
    private final Account partnerCapital = account(AccountType.CAPITAL, partner);
    private final Account buyerBalance = account(AccountType.BALANCE, buyer);
    private final Account sellerBalance = account(AccountType.BALANCE, seller);

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(accountRepository, entryRepository, contactRepository, vehicleRepository, clock);
        TestSession.open(UUID.randomUUID(), tenantId, "ADMIN");

        when(accountRepository.findByTenantIdAndTypeInOrderByCreatedAtAsc(eq(tenantId), anyCollection()))
                .thenReturn(List.of(cash, ownerCapital, partnerCapital, buyerBalance, sellerBalance));
        when(contactRepository.findAllById(any())).thenReturn(List.of(owner, partner, buyer, seller));
        // giren: kasa 9.000, alici carisi 500; cikan: sahip 6.000, ortak 2.000 (+ ortak 1.000 sonradan), satici carisi 300
        when(entryRepository.incomingTotals(tenantId)).thenReturn(List.<Object[]>of(
                new Object[]{cash.getId(), 900000L}, new Object[]{buyerBalance.getId(), 50000L}));
        when(entryRepository.outgoingTotals(tenantId)).thenReturn(List.<Object[]>of(
                new Object[]{ownerCapital.getId(), 600000L}, new Object[]{partnerCapital.getId(), 300000L},
                new Object[]{sellerBalance.getId(), 30000L}));
        when(entryRepository.vehicleExpenseTotals(tenantId, LedgerEntryType.VEHICLE_EXPENSE, AccountType.VEHICLE)).thenReturn(List.of());
        when(vehicleRepository.findByTenantIdAndStatus(tenantId, VehicleStatus.IN_STOCK)).thenReturn(List.of());
        when(vehicleRepository.findByTenantIdAndPurchaseDateBetween(any(), any(), any())).thenReturn(List.of());
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void sermayePaylari_sahipIlkSirada_yuzdeBazPuanla() {
        DashboardResponse r = service.summary();

        assertThat(r.capitalCents()).isEqualTo(900000L);
        assertThat(r.partners()).extracting(PartnerShare::name).containsExactly("Furkan Kaya", "Selin Arslan");
        assertThat(r.partners().get(0).owner()).isTrue();
        assertThat(r.partners()).extracting(PartnerShare::shareBasisPoints).containsExactly(6667, 3333);
        assertThat(r.cashCents()).isEqualTo(900000L);
    }

    @Test
    void cariArtiAlacak_cariEksiVerecek() {
        DashboardResponse r = service.summary();

        assertThat(r.receivableCents()).isEqualTo(50000L);
        assertThat(r.receivables()).singleElement().satisfies(b -> assertThat(b.name()).isEqualTo("Ayse Demir"));
        assertThat(r.payableCents()).isEqualTo(30000L);
        assertThat(r.payables()).singleElement().satisfies(b -> assertThat(b.amountCents()).isEqualTo(30000L));
    }

    @Test
    void sonAltiAy_satisKariMaliyetDusulerekHesaplanir() {
        Vehicle sold = new Vehicle();
        sold.setId(UUID.randomUUID());
        sold.setPurchaseDate(LocalDate.of(2026, 5, 2));
        sold.setSaleDate(LocalDate.of(2026, 6, 15));
        sold.setActualPurchaseCents(100000L);
        sold.setSalePriceCents(130000L);
        when(vehicleRepository.findByTenantIdAndPurchaseDateBetween(any(), any(), any())).thenReturn(List.of(sold));
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(any(), any(), any())).thenReturn(List.of(sold));
        when(entryRepository.vehicleExpenseTotals(tenantId, LedgerEntryType.VEHICLE_EXPENSE, AccountType.VEHICLE))
                .thenReturn(List.<Object[]>of(new Object[]{sold.getId(), 5000L}));

        List<MonthStat> months = service.summary().months();

        assertThat(months).hasSize(6);
        assertThat(months.get(0).month()).isEqualTo(2);
        MonthStat may = months.get(3), june = months.get(4);
        assertThat(may.bought()).isEqualTo(1);
        assertThat(june.sold()).isEqualTo(1);
        assertThat(june.revenueCents()).isEqualTo(130000L);
        assertThat(june.profitCents()).isEqualTo(25000L);
    }

    private Contact contact(String name, boolean isOwner) {
        Contact c = new Contact();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setName(name);
        c.setOwner(isOwner);
        return c;
    }

    private Account account(AccountType type, Contact c) {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setType(type);
        a.setName(c == null ? type.name() : c.getName());
        a.setContactId(c == null ? null : c.getId());
        return a;
    }
}
