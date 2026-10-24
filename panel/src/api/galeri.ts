import { apiFetch } from "./client";
import type {
  AccountResponse, BodyPart, CategoryResponse, ContactRequest, ContactResponse, ExpenseRequest, ExpenseRoot,
  LedgerEntryResponse, MonthlyTrades, PartCondition, SaleRequest, StaffSummary, VehicleCost, VehicleDetail,
  VehicleDetailAdmin, VehicleRequest, VehicleStatus, VehicleSummary,
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
  contacts: (q = "") => apiFetch<ContactResponse[]>(`/api/contacts${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`),
  addContact: (request: ContactRequest) => apiFetch<ContactResponse>("/api/contacts", json(request)),
  updateContact: (id: string, request: ContactRequest) => apiFetch<ContactResponse>(`/api/contacts/${id}`, json(request, "PUT")),
  staff: () => apiFetch<StaffSummary[]>("/api/staff"),
  monthlyTrades: (year: number, month: number) => apiFetch<MonthlyTrades>(`/api/reports/monthly-trades?year=${year}&month=${month}`),
  // Para defteri (sadece ADMIN)
  accounts: () => apiFetch<AccountResponse[]>("/api/accounts"),
  addBank: (name: string) => apiFetch<AccountResponse>("/api/accounts", json({ name })),
  ledgerEntries: (accountId: string | null) =>
    apiFetch<LedgerEntryResponse[]>(`/api/ledger-entries${accountId ? `?accountId=${accountId}` : ""}`),
  correctEntry: (id: string) => apiFetch<LedgerEntryResponse>(`/api/ledger-entries/${id}/correct`, { method: "POST" }),
  addExpense: (request: ExpenseRequest) => apiFetch<LedgerEntryResponse>("/api/expenses", json(request)),
  categories: (root: ExpenseRoot) => apiFetch<CategoryResponse[]>(`/api/expense-categories?root=${root}`),
  vehicleCost: (vehicleId: string) => apiFetch<VehicleCost>(`/api/vehicles/${vehicleId}/cost`),
};

export function isAdminDetail(d: VehicleDetail | VehicleDetailAdmin): d is VehicleDetailAdmin {
  return "vehicle" in d;
}
