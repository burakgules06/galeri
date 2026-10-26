package com.galeri.app.service.impl;

import com.galeri.app.config.BootstrapProperties;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.service.BootstrapService;
import com.galeri.app.util.PhoneUtil;
import com.galeri.app.util.NameUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BootstrapServiceImpl implements BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapServiceImpl.class);
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final BootstrapProperties props;
    private final TenantRepository tenantRepository;
    private final PanelUserRepository panelUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Result run() {
        if (!props.anySet()) return Result.NOT_CONFIGURED;
        if (!props.allSet()) {
            log.warn("Bootstrap calismadi: BOOTSTRAP_* degiskenlerinin hepsi dolu olmali");
            return Result.INCOMPLETE;
        }
        if (tenantRepository.count() > 0) {
            log.info("Bootstrap atlandi: galeri zaten var. BOOTSTRAP_* degiskenlerini .env'den silebilirsin");
            return Result.SKIPPED;
        }

        String slug = props.tenantSlug().trim();
        String phone = PhoneUtil.normalize(props.adminPhone());
        if (!SLUG.matcher(slug).matches() || slug.length() > 60) {
            log.warn("Bootstrap calismadi: BOOTSTRAP_TENANT_SLUG kucuk harf, rakam ve tire olmali");
            return Result.INVALID;
        }
        if (!PhoneUtil.isValidMobile(phone)) {
            log.warn("Bootstrap calismadi: BOOTSTRAP_ADMIN_PHONE gecerli bir cep telefonu degil");
            return Result.INVALID;
        }
        if (props.adminPassword().length() < UserServiceImpl.MIN_PASSWORD) {
            log.warn("Bootstrap calismadi: BOOTSTRAP_ADMIN_PASSWORD en az {} karakter olmali", UserServiceImpl.MIN_PASSWORD);
            return Result.INVALID;
        }
        if (panelUserRepository.existsByPhone(phone)) {
            log.warn("Bootstrap calismadi: bu telefonla kullanici zaten var ({})", PhoneUtil.mask(phone));
            return Result.INVALID;
        }

        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setDisplayName(props.tenantName().trim());
        tenant.setSubscriptionStatus("ACTIVE");
        tenantRepository.save(tenant);

        PanelUser admin = new PanelUser();
        admin.setTenant(tenant);
        admin.setPhone(phone);
        admin.setFullName(NameUtil.normalize(props.adminName()));
        admin.setRole("ADMIN");
        admin.setPasswordHash(passwordEncoder.encode(props.adminPassword()));
        panelUserRepository.save(admin);

        log.info("Bootstrap tamam: galeri {} ve yonetici {} olusturuldu. BOOTSTRAP_* degiskenlerini .env'den sil",
                slug, PhoneUtil.mask(phone));
        return Result.CREATED;
    }
}
