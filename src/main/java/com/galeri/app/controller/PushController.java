package com.galeri.app.controller;

import com.galeri.app.config.PushProperties;
import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.entity.PushSubscription;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.PushSubscriptionRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Bildirim aboneligi. Odeme bildirimleri tutar icerdigi icin sadece ADMIN abone olur.
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PushController {

    private final PushProperties props;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PanelUserRepository panelUserRepository;
    private final GenericResponseService responseService;

    public record PublicKeyResponse(String publicKey) {}

    public record SubscribeRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {
        public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {}

    /** Bos ise sunucuda bildirim kapali (panel dugmeyi gostermez). */
    @GetMapping("/public-key")
    public GenericResponse<PublicKeyResponse> publicKey() {
        return responseService.success(new PublicKeyResponse(props.configured() ? props.publicKey() : ""));
    }

    @PostMapping("/subscribe")
    @Transactional
    public GenericResponse<Void> subscribe(@Valid @RequestBody SubscribeRequest req) {
        UUID userId = CurrentUser.principal().userId();
        // Ayni cihaz baska kullaniciyla acilmis olabilir: endpoint tekil, son giris yapana baglanir
        PushSubscription sub = subscriptionRepository.findByEndpoint(req.endpoint()).orElseGet(PushSubscription::new);
        sub.setTenantId(CurrentUser.tenantId());
        sub.setPanelUser(panelUserRepository.getReferenceById(userId));
        sub.setEndpoint(req.endpoint());
        sub.setP256dh(req.keys().p256dh());
        sub.setAuth(req.keys().auth());
        subscriptionRepository.save(sub);
        return responseService.success(null);
    }

    @DeleteMapping("/subscribe")
    @Transactional
    public GenericResponse<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest req) {
        subscriptionRepository.deleteByEndpointAndPanelUser_Id(req.endpoint(), CurrentUser.principal().userId());
        return responseService.success(null);
    }
}
