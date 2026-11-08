package com.galeri.app.controller;

import com.galeri.app.exception.runtime.InvalidPhotoException;
import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.request.ShowroomRequests;
import com.galeri.app.model.response.ShowroomResponses;
import com.galeri.app.service.ShowroomService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

// Vitrin yonetimi (sadece dukkan sahibi): arac fotograflari, vitrinde gosterme, ilan fiyati / aciklamasi, WhatsApp numarasi.
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ShowroomController {

    private final ShowroomService showroomService;
    private final GenericResponseService responses;

    @GetMapping("/api/vehicles/{id}/listing")
    public GenericResponse<ShowroomResponses.Listing> listing(@PathVariable UUID id) {
        return responses.success(showroomService.listing(id));
    }

    @PutMapping("/api/vehicles/{id}/listing")
    public GenericResponse<ShowroomResponses.Listing> updateListing(@PathVariable UUID id, @Valid @RequestBody ShowroomRequests.Listing request) {
        return responses.success(showroomService.updateListing(id, request));
    }

    @PostMapping("/api/vehicles/{id}/photos")
    public GenericResponse<ShowroomResponses.Listing> addPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        try {
            return responses.success(showroomService.addPhoto(id, file.getBytes()));
        } catch (IOException e) {
            throw new InvalidPhotoException();
        }
    }

    @DeleteMapping("/api/vehicles/{id}/photos/{photoId}")
    public GenericResponse<ShowroomResponses.Listing> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        return responses.success(showroomService.deletePhoto(id, photoId));
    }

    @PutMapping("/api/vehicles/{id}/photos/order")
    public GenericResponse<ShowroomResponses.Listing> reorder(@PathVariable UUID id, @Valid @RequestBody ShowroomRequests.Reorder request) {
        return responses.success(showroomService.reorder(id, request));
    }

    @GetMapping("/api/settings/showroom")
    public GenericResponse<ShowroomResponses.Settings> settings() {
        return responses.success(showroomService.settings());
    }

    @PutMapping("/api/settings/showroom")
    public GenericResponse<ShowroomResponses.Settings> updateSettings(@Valid @RequestBody ShowroomRequests.Settings request) {
        return responses.success(showroomService.updateSettings(request));
    }
}
