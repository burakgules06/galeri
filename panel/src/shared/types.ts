// Backend'in her cevabi bu zarfla gelir (GenericResponse).
export interface ApiEnvelope<T> {
  success: boolean;
  code: number;
  message: string | null;
  data: T;
  requestId: string | null;
}

export type Role = "SUPER_ADMIN" | "ADMIN" | "STAFF";

export interface LoginResponse {
  token: string;
  fullName: string;
  role: Role;
  tenantName: string;
}

// ---- Arac (backend: model/response) ----
export type Fuel = "GASOLINE" | "DIESEL" | "LPG" | "GASOLINE_LPG" | "HYBRID" | "ELECTRIC";
export type Transmission = "MANUAL" | "AUTOMATIC" | "SEMI_AUTOMATIC";
export type VehicleStatus = "IN_STOCK" | "SOLD" | "CLOSED";
export type PartCondition = "ORIGINAL" | "LOCALLY_PAINTED" | "PAINTED" | "REPLACED";
export type BodyPart =
  | "FRONT_BUMPER" | "HOOD" | "ROOF" | "TRUNK_LID" | "REAR_BUMPER"
  | "LEFT_FRONT_FENDER" | "LEFT_FRONT_DOOR" | "LEFT_REAR_DOOR" | "LEFT_REAR_FENDER"
  | "RIGHT_FRONT_FENDER" | "RIGHT_FRONT_DOOR" | "RIGHT_REAR_DOOR" | "RIGHT_REAR_FENDER";
export type ContactType = "PERSON" | "ORGANIZATION";

export interface DamageSummary { locallyPainted: number; painted: number; replaced: number }

export interface VehicleSummary {
  id: string;
  plate: string;
  make: string;
  model: string;
  modelYear: number;
  mileageKm: number;
  fuel: Fuel;
  transmission: Transmission;
  color: string | null;
  status: VehicleStatus;
  purchaseDate: string;
  saleDate: string | null;
  daysInStock: number;
  damage: DamageSummary;
}

export interface VehicleDetail {
  id: string;
  plate: string;
  make: string;
  model: string;
  modelYear: number;
  mileageKm: number;
  fuel: Fuel;
  transmission: Transmission;
  color: string | null;
  tramerCents: number | null;
  notes: string | null;
  status: VehicleStatus;
  purchaseDate: string;
  saleDate: string | null;
  daysInStock: number;
  parts: Record<BodyPart, PartCondition>;
}

export interface ContactSummary { id: string; name: string; phone: string | null }
export interface StaffSummary { id: string; name: string | null }

// Sadece ADMIN'e gelir. STAFF'a dogrudan VehicleDetail gelir.
export interface VehicleDetailAdmin {
  vehicle: VehicleDetail;
  seller: ContactSummary | null;
  boughtBy: StaffSummary | null;
  actualPurchaseCents: number | null;
  officialPurchaseCents: number | null;
  buyer: ContactSummary | null;
  soldBy: StaffSummary | null;
  salePriceCents: number | null;
  purchaseAccountId: string | null;
}

export interface ContactResponse { id: string; name: string; phone: string | null; type: ContactType; notes: string | null }

export interface MonthlyVehicle { vehicleId: string; plate: string; make: string; model: string; date: string }
export interface StaffMonthly { staffId: string | null; name: string; bought: MonthlyVehicle[]; sold: MonthlyVehicle[] }
export interface MonthlyTrades { year: number; month: number; staff: StaffMonthly[] }

export interface VehicleRequest {
  plate: string;
  make: string;
  model: string;
  modelYear: number;
  fuel: Fuel;
  transmission: Transmission;
  mileageKm: number;
  color: string | null;
  tramerCents: number | null;
  notes: string | null;
  purchaseDate: string;
  sellerId: string | null;
  boughtById: string | null;
  actualPurchaseCents: number | null;
  officialPurchaseCents: number | null;
  purchaseAccountId: string | null;
}

export interface SaleRequest { saleDate: string; buyerId: string | null; soldById: string | null; salePriceCents: number | null }
export interface ContactRequest { name: string; phone: string | null; type: ContactType; notes: string | null }

// ---- Para defteri (sadece ADMIN). Tutarlar kurus. ----
export type AccountType = "CASH" | "BANK" | "BALANCE" | "CAPITAL" | "VEHICLE" | "EXPENSE" | "INCOME";
export type LedgerEntryType = "VEHICLE_PURCHASE" | "VEHICLE_EXPENSE" | "GENERAL_EXPENSE" | "CAPITAL";
export type ExpenseRoot = "VEHICLE" | "GENERAL";

export interface AccountSummary { id: string; type: AccountType; name: string }
export interface AccountResponse extends AccountSummary { balanceCents: number }

export interface LedgerEntryResponse {
  id: string;
  date: string;
  type: LedgerEntryType;
  source: AccountSummary;
  target: AccountSummary;
  amountCents: number;
  vehicle: { id: string; plate: string; make: string; model: string } | null;
  category: string | null;
  description: string | null;
  correctedEntryId: string | null;   // doluysa bu bir duzeltme hareketi
  corrected: boolean;                // true ise bu hareket ters hareketle iptal edildi
  createdBy: string | null;
  createdAt: string;
}

export interface CategoryResponse { id: string; name: string; root: ExpenseRoot; parentId: string | null; usage: number }

export interface VehicleCost {
  vehicleId: string;
  purchaseCents: number;
  purchaseInLedger: boolean;
  expenseCents: number;
  totalCents: number;
  salePriceCents: number | null;
  profitCents: number | null;
  entries: LedgerEntryResponse[];
}

export interface ExpenseRequest {
  date: string;
  sourceAccountId: string;
  amountCents: number;
  vehicleId: string | null;
  categoryId: string | null;
  categoryName: string | null;
  description: string | null;
}
