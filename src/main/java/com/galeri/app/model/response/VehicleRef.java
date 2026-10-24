package com.galeri.app.model.response;

import java.util.UUID;

public record VehicleRef(UUID id, String plate, String make, String model) {}
