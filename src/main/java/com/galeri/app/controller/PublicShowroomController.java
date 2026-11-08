package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.response.ShowroomResponses;
import com.galeri.app.service.ShowroomService;
import com.galeri.app.service.impl.GenericResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

// Herkese acik vitrin (giris yok). Sadece stokta ve "vitrinde goster" isaretli araclar; para / ic bilgi donmez.
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicShowroomController {

    private final ShowroomService showroomService;
    private final GenericResponseService responses;

    @GetMapping("/showroom/{slug}")
    public GenericResponse<ShowroomResponses.PublicGallery> gallery(@PathVariable String slug) {
        return responses.success(showroomService.publicGallery(slug));
    }

    @GetMapping("/showroom/{slug}/vehicles/{id}")
    public GenericResponse<ShowroomResponses.PublicVehicle> vehicle(@PathVariable String slug, @PathVariable UUID id) {
        return responses.success(showroomService.publicVehicle(slug, id));
    }

    // Dosya adi: {id}.jpg (buyuk) ya da {id}_t.jpg (kucuk). Icerik degismedigi icin uzun sure onbellekte kalir.
    @GetMapping("/photos/{file:[0-9a-fA-F-]{36}(?:_t)?\\.jpg}")
    public ResponseEntity<Resource> photo(@PathVariable String file) {
        boolean thumb = file.contains("_t");
        UUID id = UUID.fromString(file.substring(0, 36));
        Path path = showroomService.photoFile(id, thumb);
        if (path == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }
}
