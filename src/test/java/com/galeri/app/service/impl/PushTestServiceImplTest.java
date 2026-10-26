package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.PushUnavailableException;
import com.galeri.app.exception.runtime.PushDeliveryFailedException;
import com.galeri.app.model.entity.PushSubscription;
import com.galeri.app.repository.PushSubscriptionRepository;
import com.galeri.app.service.PushSender;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushTestServiceImplTest {
    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final PushSender sender = mock(PushSender.class);
    private final PushTestServiceImpl service = new PushTestServiceImpl(subscriptions, sender);

    @AfterEach void close() { TestSession.close(); }

    @Test void sadeceCagiraninCihazlarinaTestMetniGonderilir() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
        TestSession.open(user, tenant, "ADMIN");
        var first = new PushSubscription();
        var second = new PushSubscription();
        when(sender.enabled()).thenReturn(true);
        when(subscriptions.findByTenantIdAndPanelUser_Id(tenant, user)).thenReturn(List.of(first, second));
        when(sender.send(first, "Galeri", "Test bildirimi: bildirimler çalışıyor.", "/#/payments")).thenReturn(true);
        service.send();
        verify(subscriptions).findByTenantIdAndPanelUser_Id(tenant, user);
        verifyNoMoreInteractions(subscriptions);
        verify(sender).send(first, "Galeri", "Test bildirimi: bildirimler çalışıyor.", "/#/payments");
        verify(sender).send(second, "Galeri", "Test bildirimi: bildirimler çalışıyor.", "/#/payments");
    }

    @Test void vapidYoksaVeyaCihazYoksaYeniHataDoner() {
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        assertThatThrownBy(service::send).isInstanceOf(PushUnavailableException.class);
        verifyNoInteractions(subscriptions);
        when(sender.enabled()).thenReturn(true);
        assertThatThrownBy(service::send).isInstanceOf(PushUnavailableException.class);
    }

    @Test void hicbirCihazaUlasmazsaBasariliSayilmaz() {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
        TestSession.open(user, tenant, "ADMIN");
        when(sender.enabled()).thenReturn(true);
        when(subscriptions.findByTenantIdAndPanelUser_Id(tenant, user)).thenReturn(List.of(new PushSubscription()));
        assertThatThrownBy(service::send).isInstanceOf(PushDeliveryFailedException.class);
    }
}
