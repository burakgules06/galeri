package com.galeri.app.service.impl;

import com.galeri.app.model.entity.CatalogEntry;
import com.galeri.app.repository.CatalogEntryRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CatalogServiceImpl implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceImpl.class);
    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    static final String RESOURCE = "catalog/vehicles.json";

    private final CatalogEntryRepository entryRepository;
    /** Ana katalog: marka (katalog yazimi) -> modeller. Yuklendikten sonra degismez. */
    private final Map<String, List<String>> base;

    @Autowired
    public CatalogServiceImpl(CatalogEntryRepository entryRepository) {
        this(entryRepository, load(RESOURCE));
    }

    CatalogServiceImpl(CatalogEntryRepository entryRepository, Map<String, List<String>> base) {
        this.entryRepository = entryRepository;
        this.base = base;
    }

    static Map<String, List<String>> load(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (JsonNode make : root.get("makes")) {
                List<String> models = new ArrayList<>();
                for (JsonNode m : make.get("models")) models.add(m.asString());
                result.put(make.get("name").asString(), Collections.unmodifiableList(models));
            }
            log.info("Arac katalogu yuklendi: {} marka", result.size());
            return Collections.unmodifiableMap(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Arac katalogu okunamadi: " + resource, e);
        }
    }

    // ------------------------------------------------------------------ okuma

    @Override
    @Transactional(readOnly = true)
    public List<Item> makes() {
        Map<String, Item> result = new LinkedHashMap<>();
        base.keySet().forEach(m -> result.put(key(m), new Item(m, false)));
        for (CatalogEntry e : entryRepository.findByTenantId(CurrentUser.tenantId())) {
            result.putIfAbsent(key(e.getMake()), new Item(e.getMake(), true));
        }
        return sorted(result.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> models(String make) {
        Map<String, Item> result = new LinkedHashMap<>();
        baseModels(make).forEach(m -> result.put(key(m), new Item(m, false)));
        for (CatalogEntry e : entryRepository.findByTenantId(CurrentUser.tenantId())) {
            if (key(e.getMake()).equals(key(make))) result.putIfAbsent(key(e.getModel()), new Item(e.getModel(), true));
        }
        return sorted(result.values());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Resolved> resolve(UUID tenantId, String make, String model) {
        if (make == null || model == null || make.isBlank() || model.isBlank()) return Optional.empty();
        String makeKey = key(make);
        String canonicalMake = null;
        List<String> models = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : base.entrySet()) {
            if (key(e.getKey()).equals(makeKey)) {
                canonicalMake = e.getKey();
                models.addAll(e.getValue());
            }
        }
        for (CatalogEntry e : entryRepository.findByTenantId(tenantId)) {
            if (key(e.getMake()).equals(makeKey)) {
                if (canonicalMake == null) canonicalMake = e.getMake();
                models.add(e.getModel());
            }
        }
        if (canonicalMake == null) return Optional.empty();

        String value = model.trim().replaceAll("\\s+", " ");
        String valueKey = key(value);
        String best = null;
        for (String m : models) {
            String k = key(m);
            boolean match = valueKey.equals(k) || valueKey.startsWith(k + " ");
            if (match && (best == null || m.length() > best.length())) best = m;
        }
        if (best == null) return Optional.empty();
        String trim = value.substring(best.length()).trim();   // paket / motor, kullanicinin yazdigi gibi
        return Optional.of(new Resolved(canonicalMake, trim.isEmpty() ? best : best + " " + trim));
    }

    @Override
    @Transactional
    public void add(UUID tenantId, String make, String model) {
        String cleanMake = make.trim().replaceAll("\\s+", " ");
        String cleanModel = model.trim().replaceAll("\\s+", " ");
        // Marka ana katalogda varsa onun yazimi kullanilir
        String canonicalMake = base.keySet().stream().filter(m -> key(m).equals(key(cleanMake))).findFirst().orElse(cleanMake);
        if (baseOrCustomHas(tenantId, canonicalMake, cleanModel)) return;
        CatalogEntry e = new CatalogEntry();
        e.setTenantId(tenantId);
        e.setMake(canonicalMake);
        e.setModel(cleanModel);
        entryRepository.save(e);
        log.info("Kataloga eklendi: {} {}", canonicalMake, cleanModel);
    }

    // ------------------------------------------------------------------ yardimcilar

    /** Model tam olarak (paketsiz) katalogda mi. */
    private boolean baseOrCustomHas(UUID tenantId, String make, String model) {
        if (baseModels(make).stream().anyMatch(m -> key(m).equals(key(model)))) return true;
        return entryRepository.findByTenantId(tenantId).stream()
                .anyMatch(e -> key(e.getMake()).equals(key(make)) && key(e.getModel()).equals(key(model)));
    }

    private List<String> baseModels(String make) {
        String k = key(make == null ? "" : make);
        return base.entrySet().stream().filter(e -> key(e.getKey()).equals(k)).findFirst().map(Map.Entry::getValue).orElse(List.of());
    }

    /** Karsilastirma anahtari: Turkce kucuk harf, tek bosluk ("İSTANBUL" -> "istanbul"). */
    static String key(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(TURKISH);
    }

    private static List<Item> sorted(java.util.Collection<Item> items) {
        Collator tr = Collator.getInstance(TURKISH);
        return items.stream().sorted(Comparator.comparing(Item::name, tr)).toList();
    }
}
