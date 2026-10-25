package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.request.UserRequests;
import com.galeri.app.model.response.UserResponse;
import com.galeri.app.service.UserService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Kullanicilar ekrani: galerinin panel kullanicilari. Sadece yonetici.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<UserResponse>> list() {
        return responseService.success(userService.list());
    }

    @PostMapping
    public GenericResponse<UserResponse> create(@Valid @RequestBody UserRequests.Create request) {
        return responseService.success(userService.create(request));
    }

    @PatchMapping("/{id}")
    public GenericResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UserRequests.Update request) {
        return responseService.success(userService.update(id, request));
    }

    @PostMapping("/{id}/reset-password")
    public GenericResponse<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody UserRequests.ResetPassword request) {
        userService.resetPassword(id, request);
        return responseService.success(null);
    }
}
