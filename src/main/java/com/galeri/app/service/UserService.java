package com.galeri.app.service;

import com.galeri.app.model.request.UserRequests;
import com.galeri.app.model.response.UserResponse;

import java.util.List;
import java.util.UUID;

// Galerinin panel kullanicilari. Sifre degistirme haric hepsi sadece ADMIN.
public interface UserService {

    List<UserResponse> list();

    UserResponse create(UserRequests.Create request);

    /** Ad, rol, aktif/pasif. Yonetici kendini pasiflestiremez, rolunu dusuremez; en az bir aktif ADMIN kalir. */
    UserResponse update(UUID id, UserRequests.Update request);

    void resetPassword(UUID id, UserRequests.ResetPassword request);

    /** Oturumdaki kullanici kendi sifresini degistirir; mevcut sifre dogru olmali. */
    void changeOwnPassword(UserRequests.ChangePassword request);
}
