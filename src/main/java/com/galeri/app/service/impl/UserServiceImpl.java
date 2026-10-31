package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InvalidPhoneException;
import com.galeri.app.exception.runtime.LastActiveAdminException;
import com.galeri.app.exception.runtime.LoginDetailsRequiredException;
import com.galeri.app.exception.runtime.RoleChangeNotAllowedException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.repository.AccountRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.repository.VehicleShareRepository;
import com.galeri.app.exception.runtime.PhoneAlreadyUsedException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SelfChangeNotAllowedException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.exception.runtime.WrongPasswordException;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.request.UserRequests;
import com.galeri.app.model.response.UserResponse;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.UserService;
import com.galeri.app.util.PhoneUtil;
import com.galeri.app.util.NameUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    static final int MIN_PASSWORD = 8;
    private static final String ADMIN = "ADMIN";

    private final PanelUserRepository panelUserRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final VehicleShareRepository shareRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        UUID me = CurrentUser.principal().userId();
        return panelUserRepository.findByTenant_IdOrderByFullName(CurrentUser.tenantId()).stream()
                .map(u -> toResponse(u, me))
                .toList();
    }

    @Override
    @Transactional
    public UserResponse create(UserRequests.Create request) {
        boolean canLogin = request.canLogin() == null || request.canLogin();
        String phone = blankToNull(request.phone()) == null ? null : PhoneUtil.normalize(request.phone());
        if (phone != null && !PhoneUtil.isValidMobile(phone)) throw new InvalidPhoneException();
        if (canLogin && (phone == null || blankToNull(request.password()) == null)) throw new LoginDetailsRequiredException();
        if (phone != null && panelUserRepository.existsByPhone(phone)) throw new PhoneAlreadyUsedException();

        PanelUser user = new PanelUser();
        user.setTenant(tenantRepository.getReferenceById(CurrentUser.tenantId()));
        user.setPhone(phone);
        user.setFullName(NameUtil.normalize(request.fullName()));
        user.setRole(request.role());
        user.setCanLogin(canLogin);
        user.setCanTrade("STAFF".equals(request.role()) && Boolean.TRUE.equals(request.canTrade()));
        if (blankToNull(request.password()) != null) user.setPasswordHash(passwordEncoder.encode(request.password()));
        panelUserRepository.save(user);
        log.info("Kullanici eklendi: userId={}, rol={}", user.getId(), user.getRole());
        return toResponse(user, CurrentUser.principal().userId());
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UserRequests.Update request) {
        PanelUser user = find(id);
        UUID me = CurrentUser.principal().userId();
        boolean staysActiveAdmin = request.active() && ADMIN.equals(request.role());
        boolean wasActiveAdmin = user.isActive() && ADMIN.equals(user.getRole());

        // Son aktif yonetici gidiyorsa: galeri yonetilemez hale gelir
        if (wasActiveAdmin && !staysActiveAdmin
                && panelUserRepository.countByTenant_IdAndRoleAndActiveTrue(CurrentUser.tenantId(), ADMIN) <= 1) {
            throw new LastActiveAdminException();
        }
        if (user.getId().equals(me) && !staysActiveAdmin) throw new SelfChangeNotAllowedException();
        // Sermayesi / araci olan kullanici calisan yapilamaz: bir kullanicinin tek rolu var
        if ("STAFF".equals(request.role()) && user.isCapitalHolder() && holdsMoney(user)) throw new RoleChangeNotAllowedException();

        if (request.phone() != null) {
            String phone = blankToNull(request.phone()) == null ? null : PhoneUtil.normalize(request.phone());
            if (phone != null && !PhoneUtil.isValidMobile(phone)) throw new InvalidPhoneException();
            if (phone != null && !phone.equals(user.getPhone()) && panelUserRepository.existsByPhone(phone)) throw new PhoneAlreadyUsedException();
            user.setPhone(phone);
        }
        if (blankToNull(request.password()) != null) user.setPasswordHash(passwordEncoder.encode(request.password()));
        boolean canLogin = request.canLogin() == null ? user.isCanLogin() : request.canLogin();
        if (canLogin && (user.getPhone() == null || user.getPasswordHash() == null)) throw new LoginDetailsRequiredException();
        if (user.getId().equals(me) && !canLogin) throw new SelfChangeNotAllowedException();

        user.setFullName(NameUtil.normalize(request.fullName()));
        user.setRole(request.role());
        user.setActive(request.active());
        user.setCanLogin(canLogin);
        boolean canTrade = request.canTrade() == null ? user.isCanTrade() : request.canTrade();
        user.setCanTrade("STAFF".equals(request.role()) && canTrade);
        log.info("Kullanici guncellendi: userId={}, rol={}, aktif={}", id, user.getRole(), user.isActive());
        return toResponse(user, me);
    }

    @Override
    @Transactional
    public void resetPassword(UUID id, UserRequests.ResetPassword request) {
        PanelUser user = find(id);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        log.info("Sifre sifirlandi: userId={}", id);
    }

    @Override
    @Transactional
    public void changeOwnPassword(UserRequests.ChangePassword request) {
        PanelUser user = panelUserRepository.findById(CurrentUser.principal().userId()).orElseThrow(RecordNotFoundException::new);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) throw new WrongPasswordException();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        log.info("Kullanici sifresini degistirdi: userId={}", user.getId());
    }

    private PanelUser find(UUID id) {
        PanelUser user = panelUserRepository.findById(id).orElseThrow(RecordNotFoundException::new);
        if (user.getTenant() == null || !user.getTenant().getId().equals(CurrentUser.tenantId())) {
            throw new SourceNotBelongToTenantException();
        }
        return user;
    }

    /** Kullanicinin acik sermaye hesabi, arac payi ya da konsinye araci var mi. */
    private boolean holdsMoney(PanelUser u) {
        UUID tenantId = CurrentUser.tenantId();
        return accountRepository.findFirstByTenantIdAndTypeAndUserId(tenantId, AccountType.CAPITAL, u.getId()).filter(a -> !a.isClosed()).isPresent()
                || shareRepository.existsByUser_Id(u.getId())
                || vehicleRepository.existsByConsignor_Id(u.getId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static UserResponse toResponse(PanelUser u, UUID me) {
        return new UserResponse(u.getId(), u.getFullName(), u.getPhone(), u.getRole(), u.isActive(), me.equals(u.getId()),
                u.isCanLogin(), u.isCanTrade());
    }
}
