package com.galeri.app.security;

import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.repository.PanelUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calisan (STAFF) yetkileri: "arac alip satabilir" (can_trade, @staffTrade.allowed()) ve "arac masrafi girebilir"
 * (can_add_expense, @staffTrade.expenseAllowed()). @PreAuthorize'da kullanilir.
 * Yetki her istekte veritabanindan okunur: yonetici kaldirinca calisan yeniden giris yapmadan etkilenir.
 */
@Component("staffTrade")
@RequiredArgsConstructor
public class StaffTradeGuard {

    private final PanelUserRepository panelUserRepository;

    @Transactional(readOnly = true)
    public boolean allowed() {
        return staffHas(PanelUser::isCanTrade);
    }

    @Transactional(readOnly = true)
    public boolean expenseAllowed() {
        return staffHas(PanelUser::isCanAddExpense);
    }

    private boolean staffHas(java.util.function.Predicate<PanelUser> permission) {
        PanelPrincipal p;
        try {
            p = CurrentUser.principal();
        } catch (IllegalStateException e) {
            return false;
        }
        if (!"STAFF".equals(p.role())) return false;
        return panelUserRepository.findById(p.userId())
                .filter(PanelUser::isActive)
                .filter(permission)
                .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(p.tenantId()))
                .isPresent();
    }
}
