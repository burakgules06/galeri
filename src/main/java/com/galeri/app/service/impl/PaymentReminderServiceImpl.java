package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Installment;
import com.galeri.app.model.entity.NotificationLog;
import com.galeri.app.model.entity.PushSubscription;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.repository.InstallmentRepository;
import com.galeri.app.repository.NotificationLogRepository;
import com.galeri.app.repository.PushSubscriptionRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.service.PaymentReminderService;
import com.galeri.app.service.PushSender;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentReminderServiceImpl implements PaymentReminderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderServiceImpl.class);

    static final int MAX_OVERDUE_DAYS = 3;
    static final String TITLE = "Ödeme günü";
    static final String URL = "/#/payments";
    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private final InstallmentRepository installmentRepository;
    private final NotificationLogRepository logRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final VehicleRepository vehicleRepository;
    private final PushSender pushSender;
    private final TenantRepository tenantRepository;
    private final Clock clock;

    @Override
    @Transactional
    public int sendDailyReminders() {
        var dealerClock = clock.withZone(VehicleServiceImpl.DEALER_ZONE);
        var now = OffsetDateTime.now(dealerClock);
        LocalDate today = now.toLocalDate();
        var tenants = tenantRepository.findDueReminders(today, LocalTime.from(now));
        if (tenants.isEmpty()) return 0;
        Set<UUID> eligible = tenants.stream().map(t -> {
            // Taksit veya abone olmasa da bu galeri bugun kontrol edildi.
            t.setReminderSentOn(today);
            return t.getId();
        }).collect(Collectors.toSet());
        tenantRepository.saveAll(tenants);
        if (!pushSender.enabled()) {
            log.info("Odeme bildirimi atlandi: push kapali (VAPID anahtarlari yok)");
            return 0;
        }
        List<Installment> due = installmentRepository.findOpenDueBetween(today.minusDays(MAX_OVERDUE_DAYS), today).stream()
                .filter(i -> eligible.contains(i.getTenantId()) && isReminderDue(i, today))
                .toList();
        if (due.isEmpty()) return 0;

        Set<UUID> already = new HashSet<>(logRepository.notifiedOn(today, due.stream().map(Installment::getId).toList()));
        Map<UUID, List<Installment>> byTenant = due.stream()
                .filter(i -> !already.contains(i.getId()))
                .collect(Collectors.groupingBy(Installment::getTenantId, LinkedHashMap::new, Collectors.toList()));
        if (byTenant.isEmpty()) return 0;

        Map<UUID, Vehicle> vehicles = vehicleRepository.findAllById(byTenant.values().stream().flatMap(List::stream)
                        .map(i -> i.getPlan().getVehicleId()).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));

        int sent = 0;
        for (Map.Entry<UUID, List<Installment>> e : byTenant.entrySet()) {
            // Tutar iceren bildirim sadece aktif ADMIN'lere (STAFF tutar gormez)
            List<PushSubscription> subs = subscriptionRepository.findByTenantIdWithUser(e.getKey()).stream()
                    .filter(s -> "ADMIN".equals(s.getPanelUser().getRole()) && s.getPanelUser().isActive())
                    .toList();
            // Abone yoksa taksit bildirildi sayilmaz; galeri yarin yeniden kontrol edilir.
            if (subs.isEmpty()) continue;

            String body = body(e.getValue(), vehicles, today);
            boolean delivered = false;
            for (PushSubscription s : subs) delivered |= pushSender.send(s, TITLE, body, URL);
            if (!delivered) continue;

            for (Installment i : e.getValue()) {
                NotificationLog l = new NotificationLog();
                l.setTenantId(i.getTenantId());
                l.setInstallmentId(i.getId());
                l.setNotifyDate(today);
                l.setSentAt(now);
                logRepository.save(l);
            }
            sent++;
            log.info("Odeme bildirimi gonderildi: galeri {}, {} taksit, {} abonelik", e.getKey(), e.getValue().size(), subs.size());
        }
        return sent;
    }

    /** Odenmemis ve vadesi bugun ya da 1-3 gun once. 4+ gun gecikmis ve ileri vadeliler secilmez. */
    static boolean isReminderDue(Installment i, LocalDate today) {
        if (i.isPaid() || i.getDueDate().isAfter(today)) return false;
        return ChronoUnit.DAYS.between(i.getDueDate(), today) <= MAX_OVERDUE_DAYS;
    }

    /**
     * Bildirim metni. Tek kayit: "Bugün Ali Yılmaz'dan 25.000 TL senet tahsilatı var (34 ABC 123)".
     * Birden fazla: "Bugün 3 tahsilat, 1 ödeme (2 gecikmiş)".
     */
    static String body(List<Installment> items, Map<UUID, Vehicle> vehicles, LocalDate today) {
        if (items.size() == 1) {
            Installment i = items.get(0);
            boolean sale = i.getPlan().getDirection() == PlanDirection.SALE;
            String who = sale ? ablative(i.getPayer().getName()) : dative(i.getPayer().getName());
            String what = tl(i.getAmountCents()) + " " + methodWord(i.getMethod()) + (sale ? "tahsilatı" : "ödemesi");
            Vehicle v = vehicles.get(i.getPlan().getVehicleId());
            String plate = v == null ? "" : " (" + v.getPlate() + ")";
            long late = ChronoUnit.DAYS.between(i.getDueDate(), today);
            return late == 0
                    ? "Bugün " + who + " " + what + " var" + plate
                    : who + " " + what + " " + late + " gün gecikti" + plate;
        }
        long collections = items.stream().filter(i -> i.getPlan().getDirection() == PlanDirection.SALE).count();
        long payments = items.size() - collections;
        long overdue = items.stream().filter(i -> i.getDueDate().isBefore(today)).count();
        List<String> parts = new ArrayList<>();
        if (collections > 0) parts.add(collections + " tahsilat");
        if (payments > 0) parts.add(payments + " ödeme");
        return "Bugün " + String.join(", ", parts) + (overdue > 0 ? " (" + overdue + " gecikmiş)" : "");
    }

    private static String methodWord(PaymentMethod m) {
        return switch (m) {
            case PROMISSORY_NOTE -> "senet ";
            case INSTITUTION -> "kurum ";
            case TRANSFER -> "havale ";
            case CHEQUE -> "çek ";
            case CASH -> "";
        };
    }

    static String tl(long cents) {
        NumberFormat f = NumberFormat.getNumberInstance(TURKISH);
        f.setMinimumFractionDigits(cents % 100 == 0 ? 0 : 2);
        f.setMaximumFractionDigits(2);
        return f.format(cents / 100.0) + " TL";
    }

    // ---- Turkce ek: ozel isme kesme isaretiyle, unlu uyumu ve sert unsuzle

    /** "Ali Yılmaz" -> "Ali Yılmaz'dan", "Ahmet" -> "Ahmet'ten", "Demir" -> "Demir'den". */
    static String ablative(String name) {
        boolean back = backVowel(name);
        char last = lastLetter(name);
        String d = "fstkçşhp".indexOf(last) >= 0 ? "t" : "d";
        return name + "'" + d + (back ? "an" : "en");
    }

    /** "Mehmet Demir" -> "Mehmet Demir'e", "Ali" -> "Ali'ye", "Can" -> "Can'a". */
    static String dative(String name) {
        boolean back = backVowel(name);
        boolean vowelEnd = "aeıioöuü".indexOf(lastLetter(name)) >= 0;
        return name + "'" + (vowelEnd ? "y" : "") + (back ? "a" : "e");
    }

    private static char lastLetter(String name) {
        String s = name.trim().toLowerCase(TURKISH);
        for (int k = s.length() - 1; k >= 0; k--) if (Character.isLetter(s.charAt(k))) return s.charAt(k);
        return 'a';
    }

    private static boolean backVowel(String name) {
        String s = name.trim().toLowerCase(TURKISH);
        for (int k = s.length() - 1; k >= 0; k--) {
            char c = s.charAt(k);
            if ("aıou".indexOf(c) >= 0) return true;
            if ("eiöü".indexOf(c) >= 0) return false;
        }
        return true;
    }
}
