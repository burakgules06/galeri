package com.galeri.app.model.response;

import java.util.List;
import java.util.UUID;

public record NetWorthResponse(long cashCents, long bankCents, long stockCents, long receivableCents,
        long payableCents, long totalCents, long capitalCents, long openingCents, long undistributedCents,
        long differenceCents, List<CapitalResponses.Partner> partners, List<StockVehicle> vehicles) {
    public record StockVehicle(VehicleRef vehicle, long costCents, long debtCents, List<Owner> owners) {}
    public record Owner(UUID contactId, String name, int shareBp, long assetCents, long debtCents,
                        long netCents, long contributionCents) {}
}
