package com.galeri.app.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

// Testlerde galeriye kullanici ekler (V22): sermayedar ya da ikinci dukkan sahibi. Girisi kapali, telefonu yok.
public final class TestUsers {

    private TestUsers() {}

    public static UUID investor(JdbcTemplate jdbc, UUID tenant, String name) {
        return add(jdbc, tenant, name, "INVESTOR");
    }

    public static UUID add(JdbcTemplate jdbc, UUID tenant, String name, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role, can_login) values (?, ?, null, null, ?, ?, false)",
                id, tenant, name, role);
        return id;
    }
}
