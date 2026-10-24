package com.galeri.app.service;

import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.VehiclePartsRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.model.response.VehicleSummary;

import java.util.List;
import java.util.UUID;

public interface VehicleService {
    List<VehicleSummary> search(String q, VehicleStatus status);

    /** ADMIN'e VehicleDetailAdmin, STAFF'a VehicleDetail (para alanlari olmadan) doner. */
    Object detail(UUID id);

    VehicleDetailAdmin create(VehicleRequest request);

    VehicleDetailAdmin update(UUID id, VehicleRequest request);

    VehicleDetailAdmin updateParts(UUID id, VehiclePartsRequest request);

    VehicleDetailAdmin sell(UUID id, VehicleSaleRequest request);
}
