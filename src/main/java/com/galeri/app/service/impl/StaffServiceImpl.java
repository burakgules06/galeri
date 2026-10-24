package com.galeri.app.service.impl;

import com.galeri.app.model.response.StaffSummary;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private final PanelUserRepository panelUserRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StaffSummary> list() {
        return panelUserRepository.findByTenant_IdOrderByFullName(CurrentUser.tenantId()).stream()
                .filter(u -> u.isActive())
                .map(u -> new StaffSummary(u.getId(), u.getFullName()))
                .toList();
    }
}
