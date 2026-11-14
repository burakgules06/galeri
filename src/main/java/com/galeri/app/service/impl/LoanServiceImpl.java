package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.ClosedAccountException;
import com.galeri.app.exception.runtime.InvalidAccountException;
import com.galeri.app.exception.runtime.LoanExceededException;
import com.galeri.app.exception.runtime.NotAPartnerException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SameAccountException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Account;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.enums.LedgerEntryType;
import com.galeri.app.model.request.LoanRequest;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.LoanBalance;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.LedgerEntryRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.CoOwnershipService;
import com.galeri.app.service.LoanService;
import com.galeri.app.service.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private final LedgerEntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final PanelUserRepository panelUserRepository;
    private final CapitalService capitalService;
    private final CoOwnershipService coOwnershipService;
    private final NetWorthService netWorthService;

    @Override
    @Transactional
    public LedgerEntryResponse lend(LoanRequest r) {
        Parties p = parties(r);
        if (!p.lender().isOwner()) {
            // Dukkan sahibinin parasi havuzdur, sinir yok. Sermayedar o hesaptaki ve nakitteki parasindan fazlasini veremez.
            accountRepository.lockById(p.lenderCapital().getId());
            requireMoney(p.lender(), p.money(), r.amountCents(), "Borç veren");
        }
        LedgerEntry e = newEntry(LedgerEntryType.LOAN, p.borrowerCapital(), p.lenderCapital(), p.money(), r);
        String description = blankToNull(r.description());
        e.setDescription(description != null ? description
                : "Borç: " + p.lender().getFullName() + " → " + p.borrower().getFullName());
        return LedgerServiceImpl.toResponse(entryRepository.save(e), false);
    }

    @Override
    @Transactional
    public LedgerEntryResponse repay(LoanRequest r) {
        Parties p = parties(r);
        accountRepository.lockById(p.borrowerCapital().getId());
        long outstanding = outstanding(p.borrower().getId(), p.lender().getId());
        if (r.amountCents() > outstanding) {
            throw new LoanExceededException("Kalan borç " + PaymentReminderServiceImpl.tl(Math.max(0, outstanding)) + "; daha fazlası ödenemez");
        }
        if (!p.borrower().isOwner()) requireMoney(p.borrower(), p.money(), r.amountCents(), "Borçlu");
        LedgerEntry e = newEntry(LedgerEntryType.LOAN_REPAYMENT, p.lenderCapital(), p.borrowerCapital(), p.money(), r);
        String description = blankToNull(r.description());
        e.setDescription(description != null ? description
                : "Borç ödemesi: " + p.borrower().getFullName() + " → " + p.lender().getFullName());
        return LedgerServiceImpl.toResponse(entryRepository.save(e), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanBalance> balances() {
        UUID tenantId = CurrentUser.tenantId();
        Map<UUID, Map<UUID, Long>> owes = owes(tenantId);
        Map<UUID, String> names = new HashMap<>();
        List<LoanBalance> result = new ArrayList<>();
        UUID me = CurrentUser.isInvestor() ? CurrentUser.principal().userId() : null;
        owes.forEach((debtor, byCreditor) -> byCreditor.forEach((creditor, cents) -> {
            // Iki yon ayri tutulur, burada netlenir: sadece borclu taraftan pozitif olan doner
            long net = cents - owes.getOrDefault(creditor, Map.of()).getOrDefault(debtor, 0L);
            if (net <= 0) return;
            if (me != null && !me.equals(debtor) && !me.equals(creditor)) return;
            result.add(new LoanBalance(creditor, name(names, creditor), debtor, name(names, debtor), net));
        }));
        result.sort(Comparator.comparing(LoanBalance::outstandingCents).reversed());
        return result;
    }

    // ------------------------------------------------------------------ yardimcilar

    /** borclu -> alacakli -> brut tutar. Her LOAN / LOAN_REPAYMENT hareketinde kaynak hesabin sahibi hedefin sahibine borclanir
     *  (borc: alan -> veren; geri odeme ve terslemeler ters yonde oldugu icin netlenince duser). */
    private Map<UUID, Map<UUID, Long>> owes(UUID tenantId) {
        Map<UUID, Map<UUID, Long>> owes = new HashMap<>();
        for (LedgerEntry e : entryRepository.loanEntries(tenantId)) {
            UUID debtor = e.getSourceAccount().getUserId();
            UUID creditor = e.getTargetAccount().getUserId();
            if (debtor == null || creditor == null) continue;
            owes.computeIfAbsent(debtor, k -> new HashMap<>()).merge(creditor, e.getAmountCents(), Long::sum);
        }
        return owes;
    }

    /** Borclunun alacakliya kalan borcu (negatifse ters yonde borc var). */
    private long outstanding(UUID borrower, UUID lender) {
        Map<UUID, Map<UUID, Long>> owes = owes(CurrentUser.tenantId());
        return owes.getOrDefault(borrower, Map.of()).getOrDefault(lender, 0L)
                - owes.getOrDefault(lender, Map.of()).getOrDefault(borrower, 0L);
    }

    /** Sermayedar: hem o hesaptaki payi hem nakitteki sermayesi tutar kadar olmali. */
    private void requireMoney(PanelUser user, Account money, long amount, String who) {
        long inAccount = netWorthService.heldIn(money.getId(), user.getId());
        if (inAccount < amount) {
            throw new LoanExceededException(who + " " + user.getFullName() + ": " + money.getName() + " hesabında "
                    + PaymentReminderServiceImpl.tl(Math.max(0, inAccount)) + " parası var");
        }
        long cash = coOwnershipService.cashCapital(user);
        if (cash < amount) {
            throw new LoanExceededException(who + " " + user.getFullName() + ": nakitteki parası " + PaymentReminderServiceImpl.tl(Math.max(0, cash)));
        }
    }

    private record Parties(PanelUser lender, PanelUser borrower, Account lenderCapital, Account borrowerCapital, Account money) {}

    private Parties parties(LoanRequest r) {
        if (r.lenderUserId().equals(r.borrowerUserId())) throw new SameAccountException();
        PanelUser lender = holder(r.lenderUserId());
        PanelUser borrower = holder(r.borrowerUserId());
        Account money = moneyAccount(r.accountId());
        Account lenderCapital = accountRepository.findById(capitalService.capitalAccountId(lender.getId())).orElseThrow();
        Account borrowerCapital = accountRepository.findById(capitalService.capitalAccountId(borrower.getId())).orElseThrow();
        return new Parties(lender, borrower, lenderCapital, borrowerCapital, money);
    }

    private PanelUser holder(UUID id) {
        PanelUser u = panelUserRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (u.getTenant() == null || !u.getTenant().getId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (!u.isCapitalHolder()) throw new NotAPartnerException();
        return u;
    }

    private Account moneyAccount(UUID id) {
        Account a = accountRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (!a.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (!a.getType().isMoneyAccount()) throw new InvalidAccountException();
        if (a.isClosed() && entryRepository.accountBalance(a.getId()) == 0) throw new ClosedAccountException(a.getName());
        return a;
    }

    private String name(Map<UUID, String> cache, UUID userId) {
        return cache.computeIfAbsent(userId, id -> panelUserRepository.findById(id).map(PanelUser::getFullName).orElse("?"));
    }

    private LedgerEntry newEntry(LedgerEntryType type, Account source, Account target, Account money, LoanRequest r) {
        LedgerEntry e = new LedgerEntry();
        e.setTenantId(source.getTenantId());
        e.setType(type);
        e.setSourceAccount(source);
        e.setTargetAccount(target);
        e.setAmountCents(r.amountCents());
        e.setEntryDate(r.date());
        e.setLoanAccountId(money.getId());
        e.setCreatedBy(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
        return e;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
