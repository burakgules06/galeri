package com.galeri.app;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

// V22 veri tasimasi gercek Postgres'te: ayri bir semaya V21'e kadar kurulur, prod'dakine benzer veri eklenir, V22 calistirilir.
// Prod durumu: kisi altinda dukkan sahibi ve sermayedarlar, ayni adli panel kullanicilari (sermayedar calisan olarak acilmis),
// kullanicisi olmayan sermayedar, Havuz payi, konsinye araci, evrak sahibi ve plani silinmis yetim acilis hareketi.
@SpringBootTest
class UsersOwnCapitalMigrationIT {

    @Autowired private DataSource dataSource;

    private Connection connection;
    private JdbcTemplate jdbc;
    private String schema;

    private final UUID tenant = UUID.randomUUID(), testTenant = UUID.randomUUID();
    private final UUID furkanUser = UUID.randomUUID(), zekiUser = UUID.randomUUID(), burakUser = UUID.randomUUID();
    private final UUID furkan = UUID.randomUUID(), zeki = UUID.randomUUID(), yusuf = UUID.randomUUID(), ali = UUID.randomUUID();
    private final UUID testFurkan = UUID.randomUUID();
    private final UUID furkanCapital = UUID.randomUUID(), zekiCapital = UUID.randomUUID(), yusufCapital = UUID.randomUUID();
    private final UUID zekiBalance = UUID.randomUUID(), aliBalance = UUID.randomUUID(), opening = UUID.randomUUID();
    private final UUID coOwned = UUID.randomUUID(), consigned = UUID.randomUUID(), orphan = UUID.randomUUID();
    private final UUID holderPlan = UUID.randomUUID(), planOpening = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        schema = "v22it_" + Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        connection = dataSource.getConnection();
        SingleConnectionDataSource single = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(single);
        flyway(single, "21").migrate();
        jdbc.execute("set search_path to " + schema + ", public");
        seed();
        flyway(single, "22").migrate();
        jdbc.execute("set search_path to " + schema + ", public");
    }

    @AfterEach
    void tearDown() throws Exception {
        jdbc.execute("set search_path to public");
        jdbc.execute("drop schema if exists " + schema + " cascade");
        connection.close();
    }

    @Test
    void kisilerKullaniciyaTasinir_adlaEslesir_eslesmeyeneGirisiKapaliSermayedarAcilir_yetimAcilisTerslenir() {
        // Dukkan sahibi kisi -> ayni adli ADMIN; sermayesi onun, kisi kaydi silinir
        assertThat(userOf(furkanCapital)).isEqualTo(furkanUser);
        assertThat(contactExists(furkan)).isFalse();

        // Sermayedar calisan olarak acilmis: sermayedar olur; sermaye, cari, arac payi, konsinye, evrak ona gecer
        assertThat(role(zekiUser)).isEqualTo("INVESTOR");
        assertThat(userOf(zekiCapital)).isEqualTo(zekiUser);
        assertThat(userOf(zekiBalance)).isEqualTo(zekiUser);
        assertThat(jdbc.queryForObject("select consignor_user_id from vehicle where id = ?", UUID.class, consigned)).isEqualTo(zekiUser);
        assertThat(jdbc.queryForObject("select holder_user_id from payment_plan where id = ?", UUID.class, holderPlan)).isEqualTo(zekiUser);
        assertThat(contactExists(zeki)).isFalse();

        // Kullanicisi olmayan sermayedar: girisi kapali sermayedar kullanici
        UUID yusufUser = userOf(yusufCapital);
        assertThat(yusufUser).isNotNull();
        assertThat(jdbc.queryForObject("select full_name || '|' || role || '|' || can_login || '|' || coalesce(phone, '-') from panel_user where id = ?",
                String.class, yusufUser)).isEqualTo("Yusuf Göre|INVESTOR|false|-");

        // Arac paylari: kisi -> kullanici, Havuz -> dukkan sahibi
        assertThat(jdbc.queryForList("select user_id from vehicle_share where vehicle_id = ? order by share_bp desc", UUID.class, coOwned))
                .containsExactly(furkanUser, zekiUser);

        // Diger kisiler yerinde, cari kisinin
        assertThat(contactExists(ali)).isTrue();
        assertThat(jdbc.queryForObject("select contact_id from account where id = ?", UUID.class, aliBalance)).isEqualTo(ali);

        // Plani silinmis gecmis evrak acilisi ters hareketle kapanir; plani duran dokunulmaz
        assertThat(jdbc.queryForObject("select count(*) from ledger_entry where corrected_entry_id = ?", Long.class, orphan)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ledger_entry where corrected_entry_id = ?", Long.class, planOpening)).isZero();

        // Test galerisi: dukkan sahibi kisi ayni adli kullanici yoksa galerinin ADMIN'ine baglanir
        assertThat(jdbc.queryForObject("select user_id from account where tenant_id = ? and type = 'CAPITAL'", UUID.class, testTenant))
                .isEqualTo(burakUser);
        assertThat(contactExists(testFurkan)).isFalse();
    }

    // ------------------------------------------------------------------ hazirlik (V21 semasi)

    private void seed() {
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Galeri'), (?, ?, 'Test')",
                tenant, schema + "-g", testTenant, schema + "-t");
        user(furkanUser, tenant, "Furkan Mert Özçelik", "ADMIN");
        user(zekiUser, tenant, "Zeki Burak Şahin", "STAFF");
        user(burakUser, testTenant, "Burak Güleş", "ADMIN");

        contact(furkan, tenant, "Furkan Mert Özçelik", true);
        contact(zeki, tenant, "zeki burak Şahin ", false);   // buyuk / kucuk harf (ASCII) ve bosluk farki
        contact(yusuf, tenant, "Yusuf Göre", false);
        contact(ali, tenant, "Ali", false);
        contact(testFurkan, testTenant, "Furkan Mert Özçelik", true);

        account(furkanCapital, tenant, "CAPITAL", furkan);
        account(zekiCapital, tenant, "CAPITAL", zeki);
        account(yusufCapital, tenant, "CAPITAL", yusuf);
        account(zekiBalance, tenant, "BALANCE", zeki);
        account(aliBalance, tenant, "BALANCE", ali);
        account(opening, tenant, "OPENING", null);
        account(UUID.randomUUID(), testTenant, "CAPITAL", testFurkan);

        vehicle(coOwned, "CO_OWNED", null);
        jdbc.update("insert into vehicle_share (tenant_id, vehicle_id, contact_id, share_bp, profit_share_bp) values (?, ?, null, 6000, 6000), (?, ?, ?, 4000, 4000)",
                tenant, coOwned, tenant, coOwned, zeki);
        vehicle(consigned, "CONSIGNMENT", zeki);

        // Gecmis evrak: Ali bize borclu, evrak Zeki'nin (plani duruyor) ve plani silinmis yetim acilis
        entry(planOpening, opening, aliBalance, "Geçmiş alacak (senet / çek)");
        jdbc.update("""
                insert into payment_plan (id, tenant_id, vehicle_id, direction, contact_id, total_cents, created_by_id, opening_entry_id, holder_contact_id)
                values (?, ?, null, 'SALE', ?, 100000, ?, ?, ?)""", holderPlan, tenant, ali, furkanUser, planOpening, zeki);
        entry(orphan, opening, aliBalance, "Geçmiş alacak (senet / çek)");
    }

    private void user(UUID id, UUID tenantId, String name, String role) {
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', ?, ?)",
                id, tenantId, "90" + ThreadLocalRandom.current().nextLong(5_000_000_000L, 5_999_999_999L), name, role);
    }

    private void contact(UUID id, UUID tenantId, String name, boolean owner) {
        jdbc.update("insert into contact (id, tenant_id, name, type, is_owner) values (?, ?, ?, 'PERSON', ?)", id, tenantId, name, owner);
    }

    private void account(UUID id, UUID tenantId, String type, UUID contactId) {
        jdbc.update("insert into account (id, tenant_id, type, name, contact_id) values (?, ?, ?, ?, ?)", id, tenantId, type, type, contactId);
    }

    private void vehicle(UUID id, String ownership, UUID consignor) {
        boolean consignment = "CONSIGNMENT".equals(ownership);
        jdbc.update("""
                insert into vehicle (id, tenant_id, plate, make, model, model_year, fuel, transmission, purchase_date, ownership,
                                     consignor_id, commission_type, commission_rate_bp)
                values (?, ?, ?, 'Fiat', 'Egea', 2022, 'GASOLINE', 'MANUAL', current_date, ?, ?, ?, ?)""",
                id, tenant, "34 MG " + (100 + ThreadLocalRandom.current().nextInt(800)), ownership, consignor,
                consignment ? "RATE" : null, consignment ? 300 : null);
    }

    private void entry(UUID id, UUID source, UUID target, String description) {
        jdbc.update("""
                insert into ledger_entry (id, tenant_id, entry_date, type, source_account_id, target_account_id, amount_cents, description, created_by_id)
                values (?, ?, current_date, 'OPENING_BALANCE', ?, ?, 100000, ?, ?)""", id, tenant, source, target, description, furkanUser);
    }

    // ------------------------------------------------------------------ okuma

    private Flyway flyway(DataSource ds, String target) {
        return Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private UUID userOf(UUID account) {
        return jdbc.queryForObject("select user_id from account where id = ?", UUID.class, account);
    }

    private String role(UUID user) {
        return jdbc.queryForObject("select role from panel_user where id = ?", String.class, user);
    }

    private boolean contactExists(UUID id) {
        Long n = jdbc.queryForObject("select count(*) from contact where id = ?", Long.class, id);
        return n != null && n > 0;
    }
}
