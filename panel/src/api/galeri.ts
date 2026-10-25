import { apiFetch } from "./client";
import type {
  AccountResponse, BodyPart, CategoryResponse, ContactRequest, ContactResponse, DashboardResponse, ExpenseRequest, ExpenseRoot,
  YearlyTrades, PaymentPlanResponse, InstallmentResponse, InstallmentStatus, PlanDirection, ContactStatement,
  LedgerEntryResponse, MonthlyTrades, PartCondition, SaleRequest, StaffSummary, VehicleCost, VehicleDetail,
  VehicleDetailAdmin, VehicleRequest, VehicleStatus, VehicleSummary, UserResponse, UserRole,
} from "../shared/types";

const json = (body: unknown, method = "POST"): RequestInit => ({ method, body: JSON.stringify(body) });

export const api = {
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
  collect: (id: string, accountId: string, date: string) =>
    apiFetch<InstallmentResponse>(`/api/installments/${id}/collect`, json({ accountId, date })),
  undoInstallment: (id: string) => apiFetch<InstallmentResponse>(`/api/installments/${id}/undo`, { method: "POST" }),
  updateInstallment: (id: string, dueDate: string, noteNo: string | null) =>
    apiFetch<InstallmentResponse>(`/api/installments/${id}`, json({ dueDate, noteNo }, "PATCH")),
  statement: (contactId: string) => apiFetch<ContactStatement>(`/api/contacts/${contactId}/statement`),
  contacts: (q = "") => apiFetch<ContactResponse[]>(`/api/contacts${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`),
  addContact: (request: ContactRequest) => apiFetch<ContactResponse>("/api/contacts", json(request)),
  updateContact: (id: string, request: ContactRequest) => apiFetch<ContactResponse>(`/api/contacts/${id}`, json(request, "PUT")),
  staff: () => apiFetch<StaffSummary[]>("/api/staff"),
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
