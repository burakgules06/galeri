package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InstallmentAlreadyPaidException;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Gercek Postgres (AppApplicationTests gibi): iki es zamanli tahsilattan biri 1019 almali, ikinci tahsilat hareketi kalmamali.
// Birinci istek tahsil edip commit etmeden bekler; ikinci istek taksiti odenmemis gorur, hareketini yazar ve kosullu
// guncellemede satir kilidini bekler. Birinci commit edince ikincisi 0 satir gunceller -> 1019 -> transaction geri alinir.
@SpringBootTest
class ConcurrentCollectTest {

    @Autowired private PaymentPlanService paymentPlanService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void ikiEsZamanliTahsilattan_biri1019Alir_tekTahsilatHareketiKalir() throws Exception {
        UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), buyer = UUID.randomUUID(), vehicle = UUID.randomUUID();
        UUID cash = UUID.randomUUID(), plan = UUID.randomUUID(), installment = UUID.randomUUID();
        String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Test Galeri')", tenant, "test-" + suffix);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, "90" + suffix);
        jdbc.update("insert into contact (id, tenant_id, name) values (?, ?, 'Alici')", buyer, tenant);
        jdbc.update("""
                insert into vehicle (id, tenant_id, plate, make, model, model_year, fuel, transmission, purchase_date, status,
                                     sale_date, buyer_id, sale_price_cents)
                values (?, ?, '34 TS 1', 'Fiat', 'Egea', 2020, 'GASOLINE', 'MANUAL', ?, 'SOLD', ?, ?, 100000)
                """, vehicle, tenant, LocalDate.now().minusDays(10), LocalDate.now(), buyer);
        jdbc.update("insert into account (id, tenant_id, type, name) values (?, ?, 'CASH', 'Kasa')", cash, tenant);
        // Alicinin carisi satista acilir (writeSale); testte satis hareketi yazmadigimiz icin elle
        jdbc.update("insert into account (id, tenant_id, type, name, contact_id) values (?, ?, 'BALANCE', 'Alici', ?)", UUID.randomUUID(), tenant, buyer);
        jdbc.update("insert into payment_plan (id, tenant_id, vehicle_id, direction, contact_id, total_cents, created_by_id) values (?, ?, ?, 'SALE', ?, 100000, ?)",
                plan, tenant, vehicle, buyer, user);
        jdbc.update("insert into installment (id, tenant_id, plan_id, seq, due_date, amount_cents, method, payer_contact_id) values (?, ?, ?, 1, ?, 100000, 'CASH', ?)",
                installment, tenant, plan, LocalDate.now(), buyer);

        CollectRequest request = new CollectRequest(cash, LocalDate.now());
        CountDownLatch firstCollected = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                TestSession.open(user, tenant, "ADMIN");
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                        paymentPlanService.collect(installment, request);
                        firstCollected.countDown();
                        sleep(1500);   // commit etmeden bekle: ikinci istek bu arada taksiti odenmemis gorur
                    });
                } finally {
                    TestSession.close();
                }
            });
            firstCollected.await();
            Future<?> second = pool.submit(() -> {
                TestSession.open(user, tenant, "ADMIN");
                try {
                    paymentPlanService.collect(installment, request);
                } finally {
                    TestSession.close();
                }
            });

            first.get();
            assertThatThrownBy(second::get).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(InstallmentAlreadyPaidException.class);
        } finally {
            pool.shutdown();
        }

        Integer collections = jdbc.queryForObject("select count(*) from ledger_entry where vehicle_id = ? and type = 'COLLECTION'",
                Integer.class, vehicle);
        assertThat(collections).isEqualTo(1);   // ikinci istegin hareketi geri alindi
        UUID paidEntry = jdbc.queryForObject("select paid_entry_id from installment where id = ?", UUID.class, installment);
        UUID collection = jdbc.queryForObject("select id from ledger_entry where vehicle_id = ? and type = 'COLLECTION'", UUID.class, vehicle);
        assertThat(paidEntry).isEqualTo(collection);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
