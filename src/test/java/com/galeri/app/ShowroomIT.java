package com.galeri.app;

import com.galeri.app.exception.runtime.InvalidPhotoException;
import com.galeri.app.exception.runtime.PhotoLimitException;
import com.galeri.app.exception.runtime.RecordNotFoundException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.enums.AccountType;
import com.galeri.app.model.enums.ContactType;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.ShowroomRequests;
import com.galeri.app.model.request.VehicleEquipmentRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.response.ShowroomResponses;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.ContactService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.ShowroomService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Musteri vitrini: fotograf yukleme (kucultme, diske yazma), sadece isaretli stok araclarinin herkese acik listelenmesi,
// galeriler arasi ayrim.
@SpringBootTest(properties = "galeri.upload-dir=${java.io.tmpdir}/galeri-showroom-it")
class ShowroomIT {

    private static final long TL = 100;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ShowroomService showroomService;
    @Autowired private LedgerService ledgerService;
    @Autowired private CapitalService capitalService;
    @Autowired private ContactService contactService;
    @Autowired private VehicleService vehicleService;

    private final LocalDate today = LocalDate.of(2026, 5, 10);
    private UUID tenant, owner, otherTenant, otherOwner;
    private String slug, otherSlug;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        owner = UUID.randomUUID();
        otherTenant = UUID.randomUUID();
        otherOwner = UUID.randomUUID();
        String s = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        slug = "vitrin-" + s;
        otherSlug = "vitrin2-" + s;
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'ALF Motors')", tenant, slug);
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Baska Galeri')", otherTenant, otherSlug);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Sahip', 'ADMIN')",
                owner, tenant, "98" + s);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Diger', 'ADMIN')",
                otherOwner, otherTenant, "99" + s);
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void fotografYuklenir_kucultulup_diskeYazilir_kapakIlkFotograftir() throws Exception {
        TestSession.open(owner, tenant, "ADMIN");
        UUID car = vehicle("Fiat", "Egea");

        showroomService.addPhoto(car, image(3000, 2000));
        ShowroomResponses.Listing l = showroomService.addPhoto(car, image(800, 1200));

        assertThat(l.photos()).hasSize(2);
        ShowroomResponses.Photo big = l.photos().getFirst();
        assertThat(big.width()).isEqualTo(1600);
        assertThat(big.height()).isEqualTo(1067);
        assertThat(big.url()).isEqualTo("/api/public/photos/" + big.id() + ".jpg");
        Path file = showroomService.photoFile(big.id(), false);
        Path thumb = showroomService.photoFile(big.id(), true);
        assertThat(file).exists();
        assertThat(ImageIO.read(thumb.toFile()).getWidth()).isEqualTo(480);

        // Sira degisir: ikinci fotograf kapak olur
        UUID second = l.photos().get(1).id();
        l = showroomService.reorder(car, new ShowroomRequests.Reorder(List.of(second, big.id())));
        assertThat(l.photos()).extracting(ShowroomResponses.Photo::id).containsExactly(second, big.id());

        // Silinen fotografin dosyasi da gider
        l = showroomService.deletePhoto(car, second);
        assertThat(l.photos()).hasSize(1);
        assertThat(showroomService.photoFile(second, false)).isNull();
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void logoYuklenir_saydamPngKalir_yenisiEskisiniSiler_kaldirilinca_bosalir() throws Exception {
        TestSession.open(owner, tenant, "ADMIN");
        BufferedImage transparent = new BufferedImage(2400, 400, BufferedImage.TYPE_INT_ARGB);   // ince uzun logo
        transparent.getGraphics().fillRect(0, 0, 1200, 200);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(transparent, "png", bytes);

        ShowroomResponses.Settings first = showroomService.uploadLogo(bytes.toByteArray());
        assertThat(first.logoUrl()).matches("/api/public/logos/[0-9a-f-]{36}\\.png");
        UUID firstId = UUID.fromString(first.logoUrl().substring("/api/public/logos/".length(), first.logoUrl().length() - 4));
        Path firstFile = showroomService.logoFile(firstId);
        assertThat(firstFile).isNotNull();
        BufferedImage stored = ImageIO.read(firstFile.toFile());
        assertThat(stored.getWidth()).as("uzun kenar 1200").isEqualTo(1200);
        assertThat(stored.getHeight()).as("oran korunur").isEqualTo(200);
        assertThat(stored.getColorModel().hasAlpha()).as("saydamlik korunur").isTrue();

        ShowroomResponses.Settings second = showroomService.uploadLogo(image(300, 100));
        assertThat(second.logoUrl()).isNotEqualTo(first.logoUrl());
        assertThat(Files.exists(firstFile)).as("eski logo dosyasi silinir").isFalse();
        assertThat(showroomService.logoFile(firstId)).isNull();
        assertThatThrownBy(() -> showroomService.uploadLogo("logo degil".getBytes())).isInstanceOf(InvalidPhotoException.class);
        assertThat(showroomService.settings().logoUrl()).as("hatali yukleme eskisini bozmaz").isEqualTo(second.logoUrl());

        assertThat(showroomService.deleteLogo().logoUrl()).isNull();
        assertThat(showroomService.settings().logoUrl()).isNull();
    }

    @Test
    void donanimlar_temizlenipSirasiylaKaydedilir_detaydaVeVitrindeGorunur_bastanYazilir() throws Exception {
        TestSession.open(owner, tenant, "ADMIN");
        UUID v = vehicle("Renault", "Clio");
        List<String> saved = vehicleService.updateEquipment(v, new VehicleEquipmentRequest(
                List.of("Cam tavan", "  360°  kamera ", "", "cam TAVAN", "19 inç AMG jant")));
        assertThat(saved).as("bosluk toplanir, bos ve tekrar atlanir").containsExactly("Cam tavan", "360° kamera", "19 inç AMG jant");

        showroomService.updateListing(v, new ShowroomRequests.Listing(true, null, null));
        showroomService.addPhoto(v, image(400, 300));
        assertThat(vehicleDetail(v).equipment()).containsExactly("Cam tavan", "360° kamera", "19 inç AMG jant");

        vehicleService.updateEquipment(v, new VehicleEquipmentRequest(List.of("Premium ses sistemi")));
        TestSession.close();
        assertThat(showroomService.publicVehicle(slug, v).equipment()).as("bastan yazilir").containsExactly("Premium ses sistemi");
    }

    @Test
    void resimOlmayanDosya_veYirmidenFazlaFotograf_reddedilir() throws Exception {
        TestSession.open(owner, tenant, "ADMIN");
        UUID car = vehicle("Fiat", "Egea");

        assertThatThrownBy(() -> showroomService.addPhoto(car, "merhaba".getBytes())).isInstanceOf(InvalidPhotoException.class);
        byte[] small = image(40, 30);
        for (int i = 0; i < 20; i++) showroomService.addPhoto(car, small);
        assertThatThrownBy(() -> showroomService.addPhoto(car, small)).isInstanceOf(PhotoLimitException.class);
    }

    @Test
    void ilanBilgileri_ithalatMuayeneVeYetkiBelgesi_temizlenipKaydedilir() {
        TestSession.open(owner, tenant, "ADMIN");
        UUID cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        capitalService.move(CurrentUser(), new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 600_000 * TL, null));
        UUID seller = contactService.create(new ContactRequest("Satıcı İlan", null, ContactType.PERSON, null)).id();
        UUID v = vehicleService.create(new VehicleRequest("34 IL " + ThreadLocalRandom.current().nextInt(1000, 9999), "Renault", "Clio",
                (short) 2021, Fuel.GASOLINE, Transmission.MANUAL, 40_000, null, null, null, today, seller, null, 500_000 * TL, 500_000 * TL,
                cash, null, Ownership.DEALER, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                "  Distribütör   (Türkiye) ", java.time.LocalDate.of(2027, 5, 17))).vehicle().id();

        com.galeri.app.model.response.VehicleDetail detail = vehicleDetail(v);
        assertThat(detail.importStatus()).as("bosluklar toplanir").isEqualTo("Distribütör (Türkiye)");
        assertThat(detail.inspectionValidUntil()).as("ayin ilk gunu tutulur").isEqualTo(java.time.LocalDate.of(2027, 5, 1));

        ShowroomResponses.Settings saved = showroomService.updateSettings(new ShowroomRequests.Settings(null, null, "  06-1234   AB "));
        assertThat(saved.dealerLicenseNo()).isEqualTo("06-1234 AB");
        assertThat(showroomService.updateSettings(new ShowroomRequests.Settings(null, null, "   ")).dealerLicenseNo())
                .as("bos gonderilince silinir").isNull();
    }

    @Test
    void vitrin_sadeceIsaretliStokAraclariniGosterir_satilaninKaldirir() throws Exception {
        TestSession.open(owner, tenant, "ADMIN");
        UUID shown = vehicle("Renault", "Clio");
        UUID hidden = vehicle("Fiat", "Egea");
        UUID sold = vehicle("Ford", "Focus");
        showroomService.addPhoto(shown, image(400, 300));
        showroomService.updateListing(shown, new ShowroomRequests.Listing(true, 850_000 * TL, "  Hatasız, servis bakımlı  "));
        showroomService.updateListing(sold, new ShowroomRequests.Listing(true, null, null));
        ShowroomResponses.Settings saved = showroomService.updateSettings(
                new ShowroomRequests.Settings("0532 111 22 33", "  Saracalar Mah. Özal Blv.\n No:228  Akyurt/Ankara "));
        assertThat(saved.address()).as("bosluklar toplanir").isEqualTo("Saracalar Mah. Özal Blv. No:228 Akyurt/Ankara");
        assertThat(showroomService.settings().address()).isEqualTo(saved.address());
        jdbc.update("update vehicle set status = 'SOLD', sale_date = ? where id = ?", today, sold);
        TestSession.close();

        ShowroomResponses.PublicGallery g = showroomService.publicGallery(slug);

        assertThat(g.name()).isEqualTo("ALF Motors");
        assertThat(g.phone()).isEqualTo("05321112233");
        assertThat(g.vehicles()).extracting(ShowroomResponses.PublicCard::id).containsExactly(shown);
        ShowroomResponses.PublicCard card = g.vehicles().getFirst();
        assertThat(card.priceCents()).isEqualTo(850_000 * TL);
        assertThat(card.cover()).isNotNull();
        assertThat(card.photoCount()).isEqualTo(1);
        ShowroomResponses.PublicVehicle detail = showroomService.publicVehicle(slug, shown);
        assertThat(detail.description()).isEqualTo("Hatasız, servis bakımlı");
        assertThatThrownBy(() -> showroomService.publicVehicle(slug, hidden)).isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> showroomService.publicVehicle(slug, sold)).isInstanceOf(RecordNotFoundException.class);
        // Baska galerinin linkinden bu galerinin araci acilmaz
        assertThatThrownBy(() -> showroomService.publicVehicle(otherSlug, shown)).isInstanceOf(RecordNotFoundException.class);
        assertThat(showroomService.publicGallery(otherSlug).vehicles()).isEmpty();
        assertThatThrownBy(() -> showroomService.publicGallery("olmayan-galeri-" + slug)).isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void baskaGalerininAracina_fotografEklenemez_vitrinAyariDegistirilemez() throws Exception {
        TestSession.open(owner, tenant, "ADMIN");
        UUID car = vehicle("Fiat", "Egea");
        UUID photo = showroomService.addPhoto(car, image(100, 80)).photos().getFirst().id();
        TestSession.close();
        TestSession.open(otherOwner, otherTenant, "ADMIN");

        assertThatThrownBy(() -> showroomService.addPhoto(car, image(100, 80))).isInstanceOf(SourceNotBelongToTenantException.class);
        assertThatThrownBy(() -> showroomService.deletePhoto(car, photo)).isInstanceOf(SourceNotBelongToTenantException.class);
        assertThatThrownBy(() -> showroomService.updateListing(car, new ShowroomRequests.Listing(true, null, null)))
                .isInstanceOf(SourceNotBelongToTenantException.class);
    }

    private com.galeri.app.model.response.VehicleDetail vehicleDetail(UUID id) {
        return ((com.galeri.app.model.response.VehicleDetailAdmin) vehicleService.detail(id)).vehicle();
    }

    private UUID vehicle(String make, String model) {
        UUID cash = ledgerService.moneyAccounts().stream().filter(a -> a.type() == AccountType.CASH).findFirst().orElseThrow().id();
        capitalService.move(CurrentUser(), new CapitalRequests.Movement(CapitalRequests.Direction.IN, cash, today, 600_000 * TL, null));
        UUID seller = contactService.create(new ContactRequest("Satıcı " + make, null, ContactType.PERSON, null)).id();
        String plate = "34 V " + ThreadLocalRandom.current().nextInt(1000, 9999);
        return vehicleService.create(new VehicleRequest(plate, make, model, (short) 2021, Fuel.GASOLINE, Transmission.MANUAL, 40_000,
                null, null, null, today, seller, null, 500_000 * TL, 500_000 * TL, cash, null, Ownership.DEALER,
                null, null, null, null, null, null, null, null, null, null)).vehicle().id();
    }

    private UUID CurrentUser() {
        return com.galeri.app.security.CurrentUser.principal().userId();
    }

    private static byte[] image(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.getGraphics().fillRect(0, 0, w / 2, h / 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
