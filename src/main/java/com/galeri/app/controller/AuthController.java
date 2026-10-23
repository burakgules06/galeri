package com.galeri.app.controller;

import com.galeri.app.constant.ErrorMessageConstant;
import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.model.response.LoginResponse;
import com.galeri.app.model.response.MeResponse;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.security.JwtService;
import com.galeri.app.service.impl.GenericResponseService;
import com.galeri.app.util.PhoneUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final PanelUserRepository panelUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GenericResponseService responseService;

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {}

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public ResponseEntity<GenericResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        String phone = PhoneUtil.normalize(req.phone());
        PanelUser user = phone == null ? null : panelUserRepository.findByPhoneAndActiveTrue(phone).orElse(null);

        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            log.warn("Basarisiz giris denemesi: telefon={}", PhoneUtil.mask(phone));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(responseService.createResponseWithError("Telefon veya şifre hatalı", ErrorMessageConstant.INVALID_CREDENTIALS));
        }

        Tenant tenant = user.getTenant();                       // SUPER_ADMIN icin null
        UUID tenantId = tenant != null ? tenant.getId() : null;
        String token = jwtService.generateToken(user.getId(), tenantId, user.getRole());

        log.info("Basarili giris: userId={}, tenantId={}, role={}", user.getId(), tenantId, user.getRole());

        return ResponseEntity.ok(responseService.success(new LoginResponse(
                token,
                user.getFullName() != null ? user.getFullName() : "",
                user.getRole(),
                tenant != null ? tenant.getDisplayName() : "")));
    }

    /** Panel acilisinda token hala gecerli mi diye kontrol etmek icin. */
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<GenericResponse<MeResponse>> me() {
        UUID userId = CurrentUser.principal().userId();
        PanelUser user = panelUserRepository.findById(userId).filter(PanelUser::isActive).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Tenant tenant = user.getTenant();
        return ResponseEntity.ok(responseService.success(new MeResponse(
                user.getId(), user.getFullName(), user.getRole(),
                tenant != null ? tenant.getId() : null,
                tenant != null ? tenant.getDisplayName() : null)));
    }
}
