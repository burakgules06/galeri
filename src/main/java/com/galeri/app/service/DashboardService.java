package com.galeri.app.service;

import com.galeri.app.model.response.DashboardResponse;

// Ana ekran: kasa/banka, sermaye paylari, alacak/verecek, stok ve son 6 ay. Sadece ADMIN.
public interface DashboardService {
    DashboardResponse summary();
}
