package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.NotificationLog;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.PaymentPlan;
import com.galeri.app.model.entity.PushSubscription;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.NotificationLogRepository;
import com.galeri.app.repository.PushSubscriptionRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.service.PushSender;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentReminderServiceImplTest {

    @Mock private InstallmentRepository installmentRepository;
    @Mock private NotificationLogRepository logRepository;
    @Mock private PushSubscriptionRepository subscriptionRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private PushSender pushSender;

    // "Su an": 1 Temmuz 2026, 09:00 Istanbul (06:00 UTC)
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T06:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);

    private final UUID tenantId = UUID.randomUUID();
    private final List<Installment> candidates = new ArrayList<>();
    private PaymentReminderServiceImpl service;
    private Vehicle vehicle;
    private PushSubscription adminSub;

    @BeforeEach
    void setUp() {
        service = new PaymentReminderServiceImpl(installmentRepository, logRepository, subscriptionRepository, vehicleRepository, pushSender, clock);
        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setPlate("34 ABC 123");
        adminSub = subscription("ADMIN", true);

        when(pushSender.enabled()).thenReturn(true);
        when(pushSender.send(any(), anyString(), anyString(), anyString())).thenReturn(true);
        when(installmentRepository.findOpenDueBetween(TODAY.minusDays(3), TODAY)).thenReturn(candidates);
        when(logRepository.notifiedOn(eq(TODAY), anyCollection())).thenReturn(List.of());
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));
        when(subscriptionRepository.findByTenantIdWithUser(tenantId)).thenReturn(List.of(adminSub));
    }

    // ------------------------------------------------------------------ secim

    @Test
    void bugunVeBirIleUcGunGecikmisler_secilir_dortGunVeOdenmisVeIleriVade_secilmez() {
        assertThat(PaymentReminderServiceImpl.isReminderDue(installment(PlanDirection.SALE, TODAY, 100L, false), TODAY)).isTrue();
        assertThat(PaymentReminderServiceImpl.isReminderDue(installment(PlanDirection.SALE, TODAY.minusDays(1), 100L, false), TODAY)).isTrue();
        assertThat(PaymentReminderServiceImpl.isReminderDue(installment(PlanDirection.SALE, TODAY.minusDays(3), 100L, false), TODAY)).isTrue();
        assertThat(PaymentReminderServiceImpl.isReminderDue(installment(PlanDirection.SALE, TODAY.minusDays(4), 100L, false), TODAY)).isFalse();
        assertThat(PaymentReminderServiceImpl.isReminderDue(installment(PlanDirection.SALE, TODAY.plusDays(1), 100L, false), TODAY)).isFalse();
        assertThat(PaymentReminderServiceImpl.isReminderDue(installment(PlanDirection.SALE, TODAY, 100L, true), TODAY)).isFalse();
    }

    @Test
    void sadeceUygunTaksitlerIcinKayitYazilir() {
        Installment today = installment(PlanDirection.SALE, TODAY, 100L, false);
        Installment late3 = installment(PlanDirection.SALE, TODAY.minusDays(3), 200L, false);
        candidates.addAll(List.of(today, late3,
                installment(PlanDirection.SALE, TODAY.minusDays(4), 300L, false),   // sorgu disinda kalmaliydi; yine de secilmez
                installment(PlanDirection.SALE, TODAY, 400L, true)));

        assertThat(service.sendDailyReminders()).isEqualTo(1);

        ArgumentCaptor<NotificationLog> logs = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).extracting(NotificationLog::getInstallmentId).containsExactly(today.getId(), late3.getId());
        assertThat(logs.getAllValues()).allSatisfy(l -> assertThat(l.getNotifyDate()).isEqualTo(TODAY));
    }

    // ------------------------------------------------------------------ mesaj

    @Test
    void tekTahsilat_bugun_ismeEkVeTutarVePlakaIleYazilir() {
        candidates.add(installment(PlanDirection.SALE, TODAY, 2500000L, false, "Ali Yılmaz", PaymentMethod.PROMISSORY_NOTE));

        service.sendDailyReminders();

        verify(pushSender).send(adminSub, "Ödeme günü", "Bugün Ali Yılmaz'dan 25.000 TL senet tahsilatı var (34 ABC 123)", "/#/payments");
    }

    @Test
    void tekGecikmisOdeme_kacGunGectigiYazilir() {
        candidates.add(installment(PlanDirection.PURCHASE, TODAY.minusDays(2), 4000000L, false, "Mehmet Demir", PaymentMethod.PROMISSORY_NOTE));

        service.sendDailyReminders();

        verify(pushSender).send(adminSub, "Ödeme günü", "Mehmet Demir'e 40.000 TL senet ödemesi 2 gün gecikti (34 ABC 123)", "/#/payments");
    }

    @Test
    void birdenFazlaKayit_tekBildirimdeToplanir() {
        candidates.addAll(List.of(
                installment(PlanDirection.SALE, TODAY, 100L, false),
                installment(PlanDirection.SALE, TODAY, 200L, false),
                installment(PlanDirection.SALE, TODAY.minusDays(1), 300L, false),
                installment(PlanDirection.PURCHASE, TODAY, 400L, false)));

        service.sendDailyReminders();

        verify(pushSender, times(1)).send(any(), anyString(), anyString(), anyString());
        verify(pushSender).send(adminSub, "Ödeme günü", "Bugün 3 tahsilat, 1 ödeme (1 gecikmiş)", "/#/payments");
    }

    @Test
    void turkceEkler_unluUyumuVeSertUnsuzeGore() {
        assertThat(PaymentReminderServiceImpl.ablative("Ali Yılmaz")).isEqualTo("Ali Yılmaz'dan");
        assertThat(PaymentReminderServiceImpl.ablative("Ayse Demir")).isEqualTo("Ayse Demir'den");
        assertThat(PaymentReminderServiceImpl.ablative("Ahmet")).isEqualTo("Ahmet'ten");
        assertThat(PaymentReminderServiceImpl.ablative("Eminevim")).isEqualTo("Eminevim'den");
        assertThat(PaymentReminderServiceImpl.dative("Mehmet Demir")).isEqualTo("Mehmet Demir'e");
        assertThat(PaymentReminderServiceImpl.dative("Emre Can")).isEqualTo("Emre Can'a");
        assertThat(PaymentReminderServiceImpl.dative("Ali")).isEqualTo("Ali'ye");
        assertThat(PaymentReminderServiceImpl.tl(125000050L)).isEqualTo("1.250.000,50 TL");
    }

    // ------------------------------------------------------------------ tekrar, yetki

    @Test
    void ayniGunBildirimiGitmisTaksit_tekrarGonderilmez() {
        Installment done = installment(PlanDirection.SALE, TODAY, 100L, false);
        candidates.add(done);
        when(logRepository.notifiedOn(eq(TODAY), anyCollection())).thenReturn(List.of(done.getId()));

        assertThat(service.sendDailyReminders()).isZero();

        verify(pushSender, never()).send(any(), anyString(), anyString(), anyString());
        verify(logRepository, never()).save(any());
    }

    @Test
    void calisanaVePasifYoneticiye_gonderilmez() {
        PushSubscription staff = subscription("STAFF", true);
        PushSubscription inactiveAdmin = subscription("ADMIN", false);
        when(subscriptionRepository.findByTenantIdWithUser(tenantId)).thenReturn(List.of(staff, adminSub, inactiveAdmin));
        candidates.add(installment(PlanDirection.SALE, TODAY, 100L, false));

        service.sendDailyReminders();

        verify(pushSender).send(eq(adminSub), anyString(), anyString(), anyString());
        verify(pushSender, never()).send(eq(staff), anyString(), anyString(), anyString());
        verify(pushSender, never()).send(eq(inactiveAdmin), anyString(), anyString(), anyString());
    }

    @Test
    void yoneticiAbonesiYoksa_kayitYazilmaz_sonraIzinVerirseYineHatirlatilir() {
        when(subscriptionRepository.findByTenantIdWithUser(tenantId)).thenReturn(List.of(subscription("STAFF", true)));
        candidates.add(installment(PlanDirection.SALE, TODAY, 100L, false));

        assertThat(service.sendDailyReminders()).isZero();

        verify(logRepository, never()).save(any());
    }

    @Test
    void pushKapaliysa_hicbirSeyYapilmaz() {
        when(pushSender.enabled()).thenReturn(false);
        candidates.add(installment(PlanDirection.SALE, TODAY, 100L, false));

        assertThat(service.sendDailyReminders()).isZero();

        verify(installmentRepository, never()).findOpenDueBetween(any(), any());
    }

    // ------------------------------------------------------------------ yardimcilar

    private Installment installment(PlanDirection direction, LocalDate due, long amount, boolean paid) {
        return installment(direction, due, amount, paid, "Ayse Demir", PaymentMethod.TRANSFER);
    }

    private Installment installment(PlanDirection direction, LocalDate due, long amount, boolean paid, String payerName, PaymentMethod method) {
        Contact payer = new Contact();
        payer.setId(UUID.randomUUID());
        payer.setName(payerName);
        PaymentPlan plan = new PaymentPlan();
        plan.setId(UUID.randomUUID());
        plan.setTenantId(tenantId);
        plan.setVehicleId(vehicle.getId());
        plan.setDirection(direction);
        plan.setContact(payer);
        Installment i = new Installment();
        i.setId(UUID.randomUUID());
        i.setTenantId(tenantId);
        i.setPlan(plan);
        i.setSeq((short) 1);
        i.setDueDate(due);
        i.setAmountCents(amount);
        i.setMethod(method);
        i.setPayer(payer);
        if (paid) i.setPaidEntryId(UUID.randomUUID());
        return i;
    }

    private PushSubscription subscription(String role, boolean active) {
        PanelUser u = new PanelUser();
        u.setId(UUID.randomUUID());
        u.setRole(role);
        u.setActive(active);
        PushSubscription s = new PushSubscription();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setPanelUser(u);
        s.setEndpoint("https://push.example/" + UUID.randomUUID());
        return s;
    }
}
