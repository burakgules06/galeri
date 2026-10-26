package com.galeri.app.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Marka / model katalogu: ana liste resources/catalog/vehicles.json, ustune galeriye ozel eklemeler (catalog_entry).
// Model alani "temel model + paket" tasir (Passat 1.5 TSI Business): katalog temel modeli dogrular, paket serbesttir.
public interface CatalogService {

    record Item(String name, boolean custom) {}

    /** Katalogun yazimiyla marka ve model; model temel adin devamindaki paketi korur. */
    record Resolved(String make, String model) {}

    /** Ana katalog + bu galerinin eklemeleri, alfabetik. */
    List<Item> makes();

    /** Markanin modelleri (ana + galeri eklemeleri); marka bilinmiyorsa bos. */
    List<Item> models(String make);

    /**
     * Marka ve model katalogda mi: marka harf buyuklugunden bagimsiz esit, model bir katalog modeline esit ya da
     * "katalog modeli + bosluk + paket". En uzun eslesen model secilir ("Egea Cross" > "Egea").
     */
    Optional<Resolved> resolve(UUID tenantId, String make, String model);

    /** "Listede yok" ile ADMIN'in onayladigi marka / temel model galerinin katalogune eklenir (zaten varsa bir sey yapmaz). */
    void add(UUID tenantId, String make, String model);
}
