package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.service.ReminderSettingsService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/reminder")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReminderSettingsController {
    private final ReminderSettingsService service;
    private final GenericResponseService responses;
    public record Setting(@NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String time) {}

    @GetMapping
    public GenericResponse<Setting> get() {
        return responses.success(new Setting(service.time()));
    }

    @PutMapping
    public GenericResponse<Setting> update(@Valid @RequestBody Setting request) {
        return responses.success(new Setting(service.update(request.time())));
    }
}
