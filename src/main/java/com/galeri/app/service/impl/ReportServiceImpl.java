package com.galeri.app.service.impl;

import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.response.MonthlyTrades;
import com.galeri.app.model.response.MonthlyTrades.MonthlyVehicle;
import com.galeri.app.model.response.MonthlyTrades.StaffMonthly;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    static final String UNSPECIFIED = "Belirtilmemiş";

    private final VehicleRepository vehicleRepository;
    private final PanelUserRepository panelUserRepository;

    @Override
    @Transactional(readOnly = true)
    public MonthlyTrades monthlyTrades(int year, int month) {
        UUID tenantId = CurrentUser.tenantId();
        YearMonth period = YearMonth.of(year, month);
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        List<Vehicle> bought = vehicleRepository.findByTenantIdAndPurchaseDateBetween(tenantId, start, end);
        List<Vehicle> sold = vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, start, end);

        // Satirlar: ADMIN icin tum aktif calisanlar (0 olanlar dahil), STAFF icin sadece kendisi.
        Map<UUID, Row> rows = new LinkedHashMap<>();
        if (CurrentUser.isAdmin()) {
            for (PanelUser u : panelUserRepository.findByTenant_IdOrderByFullName(tenantId)) {
                if (u.isActive()) rows.put(u.getId(), new Row(u.getId(), u.getFullName()));
            }
        } else {
            UUID me = CurrentUser.principal().userId();
            panelUserRepository.findById(me).ifPresent(u -> rows.put(u.getId(), new Row(u.getId(), u.getFullName())));
        }

        distribute(bought, Vehicle::getBoughtBy, Vehicle::getPurchaseDate, rows, true);
        distribute(sold, Vehicle::getSoldBy, Vehicle::getSaleDate, rows, false);

        List<StaffMonthly> result = rows.values().stream()
                .map(Row::toResponse)
                .toList();
        return new MonthlyTrades(year, month, result);
    }

    /**
     * Araclari calisan satirlarina dagitir. Calisani girilmemis araclar sadece ADMIN'de
     * "Belirtilmemis" satirina duser; STAFF'ta satiri olmayan calisanin araclari atlanir.
     */
    private static void distribute(List<Vehicle> vehicles, Function<Vehicle, PanelUser> staff, Function<Vehicle, LocalDate> date,
                                   Map<UUID, Row> rows, boolean purchase) {
        boolean admin = CurrentUser.isAdmin();
        for (Vehicle v : vehicles) {
            PanelUser u = staff.apply(v);
            UUID staffId = u == null ? null : u.getId();
            Row row = rows.get(staffId);
            if (row == null) {
                if (!admin) continue;
                row = new Row(staffId, u == null ? UNSPECIFIED : u.getFullName());
                rows.put(staffId, row);
            }
            MonthlyVehicle item = new MonthlyVehicle(v.getId(), v.getPlate(), v.getMake(), v.getModel(), date.apply(v));
            (purchase ? row.bought : row.sold).add(item);
        }
    }

    private static final class Row {
        final UUID staffId;
        final String name;
        final List<MonthlyVehicle> bought = new ArrayList<>();
        final List<MonthlyVehicle> sold = new ArrayList<>();

        Row(UUID staffId, String name) {
            this.staffId = staffId;
            this.name = Objects.requireNonNullElse(name, UNSPECIFIED);
        }

        StaffMonthly toResponse() {
            Comparator<MonthlyVehicle> byDate = Comparator.comparing(MonthlyVehicle::date);
            bought.sort(byDate);
            sold.sort(byDate);
            return new StaffMonthly(staffId, name, List.copyOf(bought), List.copyOf(sold));
        }
    }
}
