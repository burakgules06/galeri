package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.PushDeliveryFailedException;
import com.galeri.app.exception.runtime.PushUnavailableException;
import com.galeri.app.repository.PushSubscriptionRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.PushSender;
import com.galeri.app.service.PushTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushTestServiceImpl implements PushTestService {
    private final PushSubscriptionRepository subscriptions;
    private final PushSender sender;

    @Override
    @Transactional(noRollbackFor = PushDeliveryFailedException.class)
    public void send() {
        if (!sender.enabled()) throw new PushUnavailableException();
        var devices = subscriptions.findByTenantIdAndPanelUser_Id(CurrentUser.tenantId(), CurrentUser.principal().userId());
        if (devices.isEmpty()) throw new PushUnavailableException();
        boolean delivered = false;
        for (var device : devices) {
            delivered |= sender.send(device, "Galeri", "Test bildirimi: bildirimler çalışıyor.", "/#/payments");
        }
        if (!delivered) throw new PushDeliveryFailedException();
    }
}
