package com.galeri.app.security;

import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.repository.PanelUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calisan (STAFF) icin "arac alip satabilir" (can_trade) yetkisi. @PreAuthorize'da @staffTrade.allowed() ile kullanilir.
 * Yetki her istekte veritabanindan okunur: yonetici kaldirinca calisan yeniden giris yapmadan etkilenir.
 */
@Component("staffTrade")
@RequiredArgsConstructor
public class StaffTradeGuard {

    private final PanelUserRepository panelUserRepository;

    @Transactional(readOnly = true)
    public boolean allowed() {
        PanelPrincipal p;
        try {
            p = CurrentUser.principal();
        } catch (IllegalStateException e) {
            return false;
        }
        if (!"STAFF".equals(p.role())) return false;
        return panelUserRepository.findById(p.userId())
                .filter(PanelUser::isActive)
                .filter(PanelUser::isCanTrade)
                .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(p.tenantId()))
                .isPresent();
    }
}
