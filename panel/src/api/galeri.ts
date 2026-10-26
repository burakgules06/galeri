import { apiFetch } from "./client";
import type {
  NetWorthResponse, AccountResponse, BodyPart, CategoryResponse, ContactRequest, ContactResponse, DashboardResponse, ExpenseRequest, ExpenseRoot,
  YearlyTrades, PaymentPlanResponse, InstallmentResponse, InstallmentStatus, PlanDirection, ContactStatement,
  LedgerEntryResponse, MonthlyTrades, PartCondition, SaleRequest, StaffSummary, VehicleCost, VehicleDetail,
  VehicleDetailAdmin, VehicleRequest, VehicleStatus, VehicleSummary, UserResponse, UserRole, ContactPaymentRequest,
  CapitalMovementRequest, OpeningBalanceRequest, Partner, PeriodProfit, ProfitAllocationRequest, TransferRequest, CatalogItem,
} from "../shared/types";

const json = (body: unknown, method = "POST"): RequestInit => ({ method, body: JSON.stringify(body) });

export const api = {
  reminderSettings: () => apiFetch<{ time: string }>("/api/settings/reminder"),
  updateReminderSettings: (time: string) => apiFetch<{ time: string }>("/api/settings/reminder", json({ time }, "PUT")),
  testPush: () => apiFetch<void>("/api/push/test", json({})),
  me: () => apiFetch<{ userId: string }>("/api/auth/me"),
  netWorth: () => apiFetch<NetWorthResponse>("/api/net-worth"),
  vehicles: (q: string, status: VehicleStatus | null) => {
    const p = new URLSearchParams();
    if (q.trim()) p.set("q", q.trim());
    if (status) p.set("status", status);
    return apiFetch<VehicleSummary[]>(`/api/vehicles?${p}`);
  },
  vehicle: (id: string) => apiFetch<VehicleDetail | VehicleDetailAdmin>(`/api/vehicles/${id}`),
  addVehicle: (request: VehicleRequest) => apiFetch<VehicleDetailAdmin>("/api/vehicles", json(request)),
  updateVehicle: (id: string, request: VehicleRequest) => apiFetch<VehicleDetailAdmin>(`/api/vehicles/${id}`, json(request, "PUT")),
  updateParts: (id: string, parts: Partial<Record<BodyPart, PartCondition>>) =>
    apiFetch<VehicleDetailAdmin>(`/api/vehicles/${id}/parts`, json({ parts }, "PUT")),
  sell: (id: string, request: SaleRequest) => apiFetch<VehicleDetailAdmin>(`/api/vehicles/${id}/sale`, json(request)),
  undoSale: (id: string) => apiFetch<VehicleDetailAdmin>(`/api/vehicles/${id}/sale`, { method: "DELETE" }),
  paymentPlans: (id: string) => apiFetch<PaymentPlanResponse[]>(`/api/vehicles/${id}/payment-plans`),
  installments: (f: { status?: InstallmentStatus; direction?: PlanDirection; from?: string; to?: string } = {}) => {
    const p = new URLSearchParams();
    Object.entries(f).forEach(([k, v]) => { if (v) p.set(k, v); });
    const qs = p.toString();
    return apiFetch<InstallmentResponse[]>(`/api/installments${qs ? `?${qs}` : ""}`);
  },
  collect: (id: string, accountId: string, date: string, amountCents: number | null = null) =>
    apiFetch<InstallmentResponse>(`/api/installments/${id}/collect`, json({ accountId, date, amountCents })),
  endorse: (id: string, toContactId: string, targetInstallmentId: string | null, date: string) =>
    apiFetch<InstallmentResponse>(`/api/installments/${id}/endorse`, json({ toContactId, targetInstallmentId, date })),
  bounce: (id: string) => apiFetch<InstallmentResponse>(`/api/installments/${id}/bounce`, { method: "POST" }),
  undoInstallment: (id: string) => apiFetch<InstallmentResponse>(`/api/installments/${id}/undo`, { method: "POST" }),
  updateInstallment: (id: string, dueDate: string, noteNo: string | null, bankName: string | null = null, drawerName: string | null = null) =>
    apiFetch<InstallmentResponse>(`/api/installments/${id}`, json({ dueDate, noteNo, bankName, drawerName }, "PATCH")),
  // Sermaye ve kasa islemleri (sadece ADMIN)
  partners: () => apiFetch<Partner[]>("/api/partners"),
  addPartner: (contactId: string) => apiFetch<Partner>("/api/partners", json({ contactId })),
  setOwner: (contactId: string) => apiFetch<Partner>(`/api/partners/${contactId}/owner`, { method: "PUT" }),
  moveCapital: (contactId: string, request: CapitalMovementRequest) =>
    apiFetch<LedgerEntryResponse>(`/api/partners/${contactId}/capital`, json(request)),
  periodProfit: (from: string, to: string) => apiFetch<PeriodProfit>(`/api/reports/period-profit?from=${from}&to=${to}`),
  allocateProfit: (request: ProfitAllocationRequest) => apiFetch<LedgerEntryResponse>("/api/profit-allocations", json(request)),
  transfer: (request: TransferRequest) => apiFetch<LedgerEntryResponse>("/api/transfers", json(request)),
  openingBalance: (request: OpeningBalanceRequest) => apiFetch<LedgerEntryResponse>("/api/opening-balances", json(request)),
  payContact: (contactId: string, request: ContactPaymentRequest) =>
    apiFetch<LedgerEntryResponse>(`/api/contacts/${contactId}/payments`, json(request)),
  statement: (contactId: string) => apiFetch<ContactStatement>(`/api/contacts/${contactId}/statement`),
  contacts: (q = "") => apiFetch<ContactResponse[]>(`/api/contacts${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`),
  addContact: (request: ContactRequest) => apiFetch<ContactResponse>("/api/contacts", json(request)),
  updateContact: (id: string, request: ContactRequest) => apiFetch<ContactResponse>(`/api/contacts/${id}`, json(request, "PUT")),
  staff: () => apiFetch<StaffSummary[]>("/api/staff"),
  catalogMakes: () => apiFetch<CatalogItem[]>("/api/catalog/makes"),
  catalogModels: (make: string) => apiFetch<CatalogItem[]>(`/api/catalog/makes/${encodeURIComponent(make)}/models`),
  monthlyTrades: (year: number, month: number) => apiFetch<MonthlyTrades>(`/api/reports/monthly-trades?year=${year}&month=${month}`),
  yearlyTrades: (year: number) => apiFetch<YearlyTrades>(`/api/reports/yearly-trades?year=${year}`),
  dashboard: () => apiFetch<DashboardResponse>("/api/dashboard"),
  // Para defteri (sadece ADMIN)
  accounts: () => apiFetch<AccountResponse[]>("/api/accounts"),
  addBank: (name: string) => apiFetch<AccountResponse>("/api/accounts", json({ name })),
  ledgerEntries: (accountId: string | null) =>
    apiFetch<LedgerEntryResponse[]>(`/api/ledger-entries${accountId ? `?accountId=${accountId}` : ""}`),
  correctEntry: (id: string) => apiFetch<LedgerEntryResponse>(`/api/ledger-entries/${id}/correct`, { method: "POST" }),
  addExpense: (request: ExpenseRequest) => apiFetch<LedgerEntryResponse>("/api/expenses", json(request)),
  // Duzenle: eski kayit terslenir + yeni kayit; sil: ters kayit (defter degismez)
  updateExpense: (id: string, request: ExpenseRequest) => apiFetch<LedgerEntryResponse>(`/api/expenses/${id}`, json(request, "PUT")),
  deleteExpense: (id: string) => apiFetch<LedgerEntryResponse>(`/api/expenses/${id}`, { method: "DELETE" }),
  categories: (root: ExpenseRoot) => apiFetch<CategoryResponse[]>(`/api/expense-categories?root=${root}`),
  vehicleCost: (vehicleId: string) => apiFetch<VehicleCost>(`/api/vehicles/${vehicleId}/cost`),
  // Kullanicilar (sadece ADMIN) ve herkesin kendi sifresi
  users: () => apiFetch<UserResponse[]>("/api/users"),
  addUser: (request: { fullName: string; phone: string; role: UserRole; password: string }) =>
    apiFetch<UserResponse>("/api/users", json(request)),
  updateUser: (id: string, request: { fullName: string; role: UserRole; active: boolean }) =>
    apiFetch<UserResponse>(`/api/users/${id}`, json(request, "PATCH")),
  resetUserPassword: (id: string, password: string) => apiFetch<void>(`/api/users/${id}/reset-password`, json({ password })),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiFetch<void>("/api/auth/change-password", json({ currentPassword, newPassword })),
  // Odeme gunu bildirimleri (sadece ADMIN); anahtar bossa sunucuda bildirim kapali
  pushPublicKey: () => apiFetch<{ publicKey: string }>("/api/push/public-key"),
  subscribePush: (endpoint: string, p256dh: string, auth: string) =>
    apiFetch<void>("/api/push/subscribe", json({ endpoint, keys: { p256dh, auth } })),
  unsubscribePush: (endpoint: string) => apiFetch<void>("/api/push/subscribe", json({ endpoint }, "DELETE")),
};

export function isAdminDetail(d: VehicleDetail | VehicleDetailAdmin): d is VehicleDetailAdmin {
  return "vehicle" in d;
}
