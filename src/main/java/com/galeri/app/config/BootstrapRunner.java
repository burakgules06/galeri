package com.galeri.app.config;

import com.galeri.app.service.BootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Uygulama acilisinda ilk kurulumu dener; mantik BootstrapService'te (test edilebilir).
@Component
@RequiredArgsConstructor
public class BootstrapRunner implements ApplicationRunner {

    private final BootstrapService bootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        bootstrapService.run();
    }
}
