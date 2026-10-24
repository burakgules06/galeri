package com.galeri.app.service.impl;

import com.galeri.app.model.response.CalisanOzet;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CalisanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CalisanServiceImpl implements CalisanService {

    private final PanelUserRepository panelUserRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CalisanOzet> listele() {
        return panelUserRepository.findByTenant_IdOrderByFullName(CurrentUser.tenantId()).stream()
                .filter(u -> u.isActive())
                .map(u -> new CalisanOzet(u.getId(), u.getFullName()))
                .toList();
    }
}
