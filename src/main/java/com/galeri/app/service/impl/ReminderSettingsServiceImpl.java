package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.ReminderSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class ReminderSettingsServiceImpl implements ReminderSettingsService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final TenantRepository tenants;

    @Override
    @Transactional(readOnly = true)
    public String time() {
        return tenants.findById(CurrentUser.tenantId()).orElseThrow(RecordNotFoundException::new).getReminderTime().format(FORMAT);
    }

    @Override
    @Transactional
    public String update(String time) {
        var tenant = tenants.lockById(CurrentUser.tenantId()).orElseThrow(RecordNotFoundException::new);
        tenant.setReminderTime(LocalTime.parse(time, FORMAT));
        tenant.setReminderSentOn(null);
        tenants.save(tenant);
        return tenant.getReminderTime().format(FORMAT);
    }
}
