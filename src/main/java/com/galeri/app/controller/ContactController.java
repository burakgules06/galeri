package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.response.ContactResponse;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Kisiler (musteri, satici, ortak, kurum) sadece galeri sahibine acik.
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ContactController {

    private final ContactService contactService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<ContactResponse>> search(@RequestParam(required = false) String q) {
        return responseService.success(contactService.search(q));
    }

    @PostMapping
    public GenericResponse<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        return responseService.success(contactService.create(request));
    }

    @PutMapping("/{id}")
    public GenericResponse<ContactResponse> update(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        return responseService.success(contactService.update(id, request));
    }
}
