package com.galeri.app.repository;

import com.galeri.app.model.entity.PanelUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PanelUserRepository extends JpaRepository<PanelUser, UUID> {
    Optional<PanelUser> findByPhoneAndActiveTrue(String phone);
}
