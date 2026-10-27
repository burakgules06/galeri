package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.CapitalExceededException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.InvalidPeriodException;
import com.galeri.app.exception.runtime.NotAPartnerException;
import com.galeri.app.exception.runtime.PartnerNotRemovableException;
import com.galeri.app.exception.runtime.SameAccountException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.response.CapitalResponses.Partner;
import com.galeri.app.model.response.CapitalResponses.PartnerProfit;
import com.galeri.app.model.response.CapitalResponses.PeriodProfit;
import com.galeri.app.model.response.CapitalResponses.SoldVehicle;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.VehicleRef;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.ContactRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.util.ConsignmentMath;
import com.galeri.app.util.MoneyMath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CapitalServiceImpl implements CapitalService {

    private static final DateTimeFormatter TR_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final ContactRepository contactRepository;
    private final VehicleRepository vehicleRepository;
    private final PanelUserRepository panelUserRepository;
    private final CoOwnershipService coOwnershipService;
    private final Clock clock;

    // ------------------------------------------------------------------ ortaklar

    @Override
    @Transactional(readOnly = true)
    public List<Partner> partners() {
        UUID tenantId = CurrentUser.tenantId();
        List<Account> accounts = capitalAccounts(tenantId);
        Map<UUID, Long> capital = capitalByAccount(entryRepository.touchingType(tenantId, AccountType.CAPITAL), null);
        Map<UUID, Long> bound = coOwnershipService.boundCapital(tenantId, today());
        Map<UUID, Contact> contacts = contacts(accounts);
        long total = accounts.stream().mapToLong(a -> Math.max(0, capital.getOrDefault(a.getId(), 0L))).sum();
        return accounts.stream()
                .map(a -> {
                    Contact c = contacts.get(a.getContactId());
                    long cap = capital.getOrDefault(a.getId(), 0L);
                    long inVehicles = bound.getOrDefault(a.getContactId(), 0L);
                    return new Partner(a.getContactId(), c != null ? c.getName() : a.getName(), c != null ? c.getPhone() : null,
                            c != null && c.isOwner(), cap, cap - inVehicles, inVehicles, MoneyMath.basisPoints(Math.max(0, cap), total));
                })
                .sorted(Comparator.comparing(Partner::owner).reversed().thenComparing(Comparator.comparingLong(Partner::capitalCents).reversed()))
                .toList();
    }

    @Override
    @Transactional
    public Partner addPartner(CapitalRequests.Partner request) {
        Contact contact = getContact(request.contactId());
        capitalAccount(contact, true);
        if (request.base() != null) {
            contact.setCapitalBase(request.base());
            contactRepository.save(contact);
        }
        return find(contact.getId());
    }

    @Override
    @Transactional
    public Partner setOwner(UUID contactId) {
        Contact contact = getContact(contactId);
        if (capitalAccount(contact, false) == null) throw new NotAPartnerException();
        contactRepository.findFirstByTenantIdAndOwnerTrue(contact.getTenantId())
                .filter(c -> !c.getId().equals(contact.getId()))
                .ifPresent(old -> {
                    old.setOwner(false);
                    contactRepository.saveAndFlush(old);   // uq_contact_owner: eski sahip once kalkmali
                });
        contact.setOwner(true);
        contactRepository.saveAndFlush(contact);
        return find(contact.getId());
    }

    @Override
    @Transactional
    public Partner setBase(UUID contactId, CapitalRequests.Base request) {
        Contact contact = getContact(contactId);
        if (capitalAccount(contact, false) == null) throw new NotAPartnerException();
        contact.setCapitalBase(request.base());
        contactRepository.save(contact);
        return find(contact.getId());
    }

    @Override
    @Transactional
    public void removePartner(UUID contactId) {
        Contact contact = getContact(contactId);
        if (contact.isOwner()) throw new PartnerNotRemovableException("Dükkân sahibi kaldırılamaz. Önce başka birini sahip yapın.");
        Account capital = capitalAccount(contact, false);
        if (capital == null) throw new NotAPartnerException();
        accountRepository.lockById(capital.getId());
        UUID tenantId = contact.getTenantId();
        if (coOwnershipService.boundCapital(tenantId, today()).getOrDefault(contactId, 0L) > 0) {
            throw new PartnerNotRemovableException("Araçta parası var. Önce o araçlar satılmalı.");
        }
        List<LedgerEntry> touching = entryRepository.touchingType(tenantId, AccountType.CAPITAL);
        long remaining = capitalByAccount(touching, null).getOrDefault(capital.getId(), 0L);

        // Hesap basina net giris (giris - cekis): para nereden girdiyse oraya doner
        Map<UUID, Long> net = new LinkedHashMap<>();
        Map<UUID, Account> money = new HashMap<>();
        for (LedgerEntry e : touching) {
            if (e.getType() != LedgerEntryType.CAPITAL) continue;
            Account other = e.getSourceAccount().getId().equals(capital.getId()) ? e.getTargetAccount()
                    : e.getTargetAccount().getId().equals(capital.getId()) ? e.getSourceAccount() : null;
            if (other == null || (other.getType() != AccountType.CASH && other.getType() != AccountType.BANK)) continue;
            money.putIfAbsent(other.getId(), other);
            net.merge(other.getId(), e.getSourceAccount().getId().equals(capital.getId()) ? e.getAmountCents() : -e.getAmountCents(), Long::sum);
        }
        List<Map.Entry<UUID, Long>> order = net.entrySet().stream().filter(x -> x.getValue() > 0)
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()).toList();
        for (Map.Entry<UUID, Long> x : order) {
            if (remaining <= 0) break;
            long amount = Math.min(remaining, x.getValue());
            refund(money.get(x.getKey()), capital, amount);
            remaining -= amount;
        }
        if (remaining > 0) {   // aktarim ya da kar payindan gelen kisim kasadan
            refund(accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.CASH).orElseThrow(RecordNotFoundException::new),
                    capital, remaining);
        }
        capital.setClosed(true);
        accountRepository.save(capital);
    }

    private void refund(Account money, Account capital, long amount) {
        LedgerEntry e = newEntry(LedgerEntryType.CAPITAL, money, capital, amount, today());
        e.setDescription("Ortaklıktan çıkış: iade");
        entryRepository.save(e);
    }

    @Override
    @Transactional
    public LedgerEntryResponse transferCapital(CapitalRequests.CapitalTransfer r) {
        if (r.fromContactId().equals(r.toContactId())) throw new SameAccountException();
        Contact from = getContact(r.fromContactId());
        Contact to = getContact(r.toContactId());
        Account fromCapital = capitalAccount(from, from.isOwner());   // dukkan sahibinin hesabi yoksa acilir
        if (fromCapital == null) throw new NotAPartnerException();
        Account toCapital = capitalAccount(to, true);                 // alan kisi sermayedar olur
        if (!from.isOwner()) {
            accountRepository.lockById(fromCapital.getId());
            long cash = capitalByAccount(entryRepository.touchingType(from.getTenantId(), AccountType.CAPITAL), null)
                    .getOrDefault(fromCapital.getId(), 0L)
                    - coOwnershipService.boundCapital(from.getTenantId(), today()).getOrDefault(from.getId(), 0L);
            if (r.amountCents() > cash) throw new CapitalExceededException();
        }
        LedgerEntry e = newEntry(LedgerEntryType.CAPITAL_TRANSFER, toCapital, fromCapital, r.amountCents(), r.date());
        String description = blankToNull(r.description());
        e.setDescription(description != null ? description : "Sermaye aktarımı: " + from.getName() + " → " + to.getName());
        return LedgerServiceImpl.toResponse(entryRepository.save(e), false);
    }

    @Override
    @Transactional
    public LedgerEntryResponse move(UUID contactId, CapitalRequests.Movement r) {
        Contact contact = getContact(contactId);
        Account money = moneyAccount(r.accountId());
        boolean in = r.direction() == CapitalRequests.Direction.IN;
        Account capital = capitalAccount(contact, in || contact.isOwner());   // giris kisiyi ortak yapar; cekis icin ortak olmali
        if (capital == null) throw new NotAPartnerException();
        // Dukkan sahibinin cekisi sinirlanmaz: sermayesi havuzdan sermayedarlar cikinca kalandir (panel kasadaki payi bitince uyarir)
        if (!in && !contact.isOwner()) {
            accountRepository.lockById(capital.getId());   // es zamanli iki cekis ayni bakiyeyi gormesin
            // Cekis en fazla nakitteki sermaye kadar: araclarda bagli kisim arac satilinca nakde doner
            long current = capitalByAccount(entryRepository.touchingType(contact.getTenantId(), AccountType.CAPITAL), null)
                    .getOrDefault(capital.getId(), 0L)
                    - coOwnershipService.boundCapital(contact.getTenantId(), today()).getOrDefault(contact.getId(), 0L);
            if (r.amountCents() > current) throw new CapitalExceededException();
        }
        LedgerEntry e = in
                ? newEntry(LedgerEntryType.CAPITAL, capital, money, r.amountCents(), r.date())
                : newEntry(LedgerEntryType.CAPITAL, money, capital, r.amountCents(), r.date());
        String description = blankToNull(r.description());
        e.setDescription(description != null ? description : in ? "Sermaye girişi" : "Sermaye çekişi");
        return LedgerServiceImpl.toResponse(entryRepository.save(e), false);
    }

    // ------------------------------------------------------------------ donem kari

    @Override
    @Transactional(readOnly = true)
    public PeriodProfit periodProfit(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new InvalidPeriodException();
        UUID tenantId = CurrentUser.tenantId();

        Map<UUID, Long> expenses = new HashMap<>();
        for (Object[] s : entryRepository.vehicleExpenseTotals(tenantId, LedgerEntryType.VEHICLE_EXPENSE, AccountType.VEHICLE)) {
            expenses.put((UUID) s[0], ((Number) s[1]).longValue());
        }
        Map<UUID, Long> partnerShares = new HashMap<>();
        for (Object[] s : entryRepository.partnerProfitShareTotals(tenantId, LedgerEntryType.VEHICLE_PROFIT_SHARE, AccountType.CAPITAL)) {
            partnerShares.put((UUID) s[0], ((Number) s[1]).longValue());
        }
        List<SoldVehicle> vehicles = new ArrayList<>();
        long vehicleProfit = 0, commission = 0, coOwned = 0;
        for (Vehicle v : vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, from, to)) {
            if (v.getStatus() == VehicleStatus.IN_STOCK || v.getSalePriceCents() == null) continue;
            VehicleRef ref = new VehicleRef(v.getId(), v.getPlate(), v.getMake(), v.getModel());
            if (v.getOwnership() == Ownership.CONSIGNMENT) {
                Long c = ConsignmentMath.commission(v, v.getSalePriceCents());
                long amount = c == null ? 0 : c;
                commission += amount;
                vehicles.add(new SoldVehicle(ref, v.getOwnership(), v.getSaleDate(), v.getSalePriceCents(), null, amount, amount));
            } else {
                long cost = (v.getActualPurchaseCents() == null ? 0 : v.getActualPurchaseCents()) + expenses.getOrDefault(v.getId(), 0L);
                long profit = v.getSalePriceCents() - cost;
                // Ortak arac: ortaklarin payi satista sermayelerine gecti (defterde); kalan havuzun payi (dukkan payi dahil)
                long partners = v.getOwnership() == Ownership.CO_OWNED ? partnerShares.getOrDefault(v.getId(), 0L) : 0;
                vehicleProfit += profit - partners;
                coOwned += partners;
                vehicles.add(new SoldVehicle(ref, v.getOwnership(), v.getSaleDate(), v.getSalePriceCents(), cost, profit, profit - partners));
            }
        }
        vehicles.sort(Comparator.comparing(SoldVehicle::saleDate));

        // Genel giderler: donemde gider hesabina net giris (konsinye aracta galerinin ustlendigi masraf da burada)
        long general = entryRepository.netInto(tenantId, AccountType.EXPENSE, from, to);
        long net = vehicleProfit + commission - general;

        return new PeriodProfit(from, to, vehicles, vehicleProfit, commission, general, net, coOwned,
                partnerProfits(tenantId, from, to, net));
    }

    /**
     * Paylar donem sonundaki nakitteki sermayelerden: araclarda bagli kisim havuz karindan pay almaz; bu donemin karindan
     * sermayeye eklenenler de haric (yoksa kar payi eklendikce oneri kayar). Onerilen tutar net kardan bu paylarla (bilgi).
     */
    private List<PartnerProfit> partnerProfits(UUID tenantId, LocalDate from, LocalDate to, long net) {
        List<Account> accounts = capitalAccounts(tenantId);
        List<LedgerEntry> capitalEntries = entryRepository.touchingType(tenantId, AccountType.CAPITAL);
        Map<UUID, Long> allocated = allocatedByAccount(capitalEntries, from, to);
        Map<UUID, Long> bound = coOwnershipService.boundCapital(tenantId, to);
        Map<UUID, UUID> contactOf = accounts.stream().collect(Collectors.toMap(Account::getId, Account::getContactId));
        Map<UUID, Long> capital = new HashMap<>();
        capitalByAccount(capitalEntries, to).forEach((id, c) -> capital.put(id,
                c - allocated.getOrDefault(id, 0L) - bound.getOrDefault(contactOf.get(id), 0L)));
        Map<UUID, Contact> contacts = contacts(accounts);

        List<Account> involved = accounts.stream()
                .filter(a -> capital.getOrDefault(a.getId(), 0L) > 0 || allocated.getOrDefault(a.getId(), 0L) != 0)
                .sorted(Comparator.comparingLong((Account a) -> capital.getOrDefault(a.getId(), 0L)).reversed())
                .toList();
        List<Long> weights = involved.stream().map(a -> Math.max(0, capital.getOrDefault(a.getId(), 0L))).toList();
        long total = weights.stream().mapToLong(Long::longValue).sum();
        List<Long> suggested = MoneyMath.splitByWeights(net, weights);

        List<PartnerProfit> result = new ArrayList<>();
        for (int k = 0; k < involved.size(); k++) {
            Account a = involved.get(k);
            Contact c = contacts.get(a.getContactId());
            result.add(new PartnerProfit(a.getContactId(), c != null ? c.getName() : a.getName(), c != null && c.isOwner(),
                    weights.get(k), MoneyMath.basisPoints(weights.get(k), total), suggested.get(k), allocated.getOrDefault(a.getId(), 0L)));
        }
        return result;
    }

    @Override
    @Transactional
    public LedgerEntryResponse allocate(CapitalRequests.Allocation r) {
        if (r.periodFrom().isAfter(r.periodTo())) throw new InvalidPeriodException();
        Contact contact = getContact(r.contactId());
        Account capital = capitalAccount(contact, false);
        if (capital == null) throw new NotAPartnerException();
        // Gelirdeki kar ortagin sermayesine gecer: sermaye hesabindan cikis = sermaye artar (giris CAPITAL -> kasa ile ayni yon)
        LedgerEntry e = newEntry(LedgerEntryType.PROFIT_ALLOCATION, capital, incomeAccount(contact.getTenantId()), r.amountCents(), r.date());
        e.setDescription("Kâr payı sermayeye: " + TR_DATE.format(r.periodFrom()) + " – " + TR_DATE.format(r.periodTo()));
        return LedgerServiceImpl.toResponse(entryRepository.save(e), false);
    }

    // ------------------------------------------------------------------ hesap

    /**
     * Ortagin sermayesi = sermaye hesabindan cikan - giren (giris CAPITAL -> kasa, cekis kasa -> CAPITAL,
     * kar payi CAPITAL -> INCOME). upTo doluysa o gun dahil o gune kadar. Duzeltmeler ters yonde oldugu icin kendiliginden duser.
     */
    static Map<UUID, Long> capitalByAccount(List<LedgerEntry> entries, LocalDate upTo) {
        Map<UUID, Long> result = new HashMap<>();
        for (LedgerEntry e : entries) {
            if (upTo != null && e.getEntryDate().isAfter(upTo)) continue;
            if (e.getSourceAccount().getType() == AccountType.CAPITAL) result.merge(e.getSourceAccount().getId(), e.getAmountCents(), Long::sum);
            if (e.getTargetAccount().getType() == AccountType.CAPITAL) result.merge(e.getTargetAccount().getId(), -e.getAmountCents(), Long::sum);
        }
        return result;
    }

    /** Donem icinde (iki uc dahil) sermayeye eklenen kar payi, net (duzeltmeler duser). */
    static Map<UUID, Long> allocatedByAccount(List<LedgerEntry> entries, LocalDate from, LocalDate to) {
        Map<UUID, Long> result = new HashMap<>();
        for (LedgerEntry e : entries) {
            if (e.getType() != LedgerEntryType.PROFIT_ALLOCATION || e.getEntryDate().isBefore(from) || e.getEntryDate().isAfter(to)) continue;
            if (e.getSourceAccount().getType() == AccountType.CAPITAL) result.merge(e.getSourceAccount().getId(), e.getAmountCents(), Long::sum);
            if (e.getTargetAccount().getType() == AccountType.CAPITAL) result.merge(e.getTargetAccount().getId(), -e.getAmountCents(), Long::sum);
        }
        return result;
    }

    private Partner find(UUID contactId) {
        return partners().stream().filter(p -> p.contactId().equals(contactId)).findFirst().orElseThrow(RecordNotFoundException::new);
    }

    private List<Account> capitalAccounts(UUID tenantId) {
        return accountRepository.findByTenantIdAndTypeInOrderByCreatedAtAsc(tenantId, List.of(AccountType.CAPITAL)).stream()
                .filter(a -> !a.isClosed()).toList();
    }

    private Map<UUID, Contact> contacts(List<Account> accounts) {
        return contactRepository.findAllById(accounts.stream().map(Account::getContactId).distinct().toList()).stream()
                .collect(Collectors.toMap(Contact::getId, Function.identity()));
    }

    /** Ortagin sermaye hesabi; yoksa (ya da kapaliysa) create ise acilir, degilse null. */
    private Account capitalAccount(Contact contact, boolean create) {
        Account found = accountRepository.findFirstByTenantIdAndTypeAndContactId(contact.getTenantId(), AccountType.CAPITAL, contact.getId())
                .orElse(null);
        if (found != null && !found.isClosed()) return found;
        if (!create) return null;
        if (found != null) {   // kaldirilan ortak yeniden ortak yapildi
            found.setClosed(false);
            return accountRepository.save(found);
        }
        Account a = new Account();
        a.setTenantId(contact.getTenantId());
        a.setType(AccountType.CAPITAL);
        a.setContactId(contact.getId());
        a.setName(contact.getName());
        return accountRepository.save(a);
    }

    private Account incomeAccount(UUID tenantId) {
        return accountRepository.findFirstByTenantIdAndType(tenantId, AccountType.INCOME).orElseGet(() -> {
            Account a = new Account();
            a.setTenantId(tenantId);
            a.setType(AccountType.INCOME);
            a.setName("Gelirler");
            return accountRepository.save(a);
        });
    }

    private Account moneyAccount(UUID id) {
        Account a = accountRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!a.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (!a.getType().isMoneyAccount()) throw new InvalidAccountException();
        return a;
    }

    private Contact getContact(UUID id) {
        Contact c = contactRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!c.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return c;
    }

    private LedgerEntry newEntry(LedgerEntryType type, Account source, Account target, long amount, LocalDate date) {
        LedgerEntry e = new LedgerEntry();
        e.setTenantId(source.getTenantId());
        e.setType(type);
        e.setSourceAccount(source);
        e.setTargetAccount(target);
        e.setAmountCents(amount);
        e.setEntryDate(date);
        e.setCreatedBy(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
        return e;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(VehicleServiceImpl.DEALER_ZONE));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
