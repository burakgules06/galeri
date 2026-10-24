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
  buyer: ContactSummary | null;   // sadece ADMIN'de dolu
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

export type TradeKind = "BOUGHT" | "SOLD";
export interface TradeItem {
  vehicleId: string; plate: string; make: string; model: string; modelYear: number; mileageKm: number;
  kind: TradeKind; date: string; staffId: string | null; staffName: string;
}
export interface YearlyTrades { year: number; staff: StaffSummary[]; items: TradeItem[] }

// ---- Ana ekran (sadece ADMIN). Tutarlar kurus, paylar baz puan (10000 = %100). ----
export interface PartnerShare { contactId: string; name: string; owner: boolean; capitalCents: number; shareBasisPoints: number }
export interface ContactBalance { contactId: string; name: string; phone: string | null; amountCents: number }
export interface MonthStat { year: number; month: number; bought: number; sold: number; revenueCents: number; profitCents: number }
export interface DashboardResponse {
  cashCents: number;
  bankCents: number;
  capitalCents: number;
  receivableCents: number;
  payableCents: number;
  partners: PartnerShare[];
  receivables: ContactBalance[];
  payables: ContactBalance[];
  inStockCount: number;
  stockCostCents: number;
  averageDaysInStock: number;
  months: MonthStat[];
  overdueCount: number;            // geciken tahsilat adedi
  overdueCents: number;
  week: InstallmentResponse[];     // bugun + 6 gun, odenmemis (tahsilat ve odeme)
  weekCollectCents: number;
  weekPayCents: number;
}

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

export interface SaleRequest {
  saleDate: string;
  buyerId: string;
  soldById: string | null;
  salePriceCents: number;
  installments: InstallmentRequest[];   // toplami salePriceCents'e kurusu kurusuna esit
}

// ---- Odeme plani ve taksitler (sadece ADMIN). Tutarlar kurus. ----
export type PlanDirection = "SALE" | "PURCHASE";
export type PaymentMethod = "CASH" | "TRANSFER" | "PROMISSORY_NOTE" | "INSTITUTION";
export type InstallmentStatus = "PAID" | "OVERDUE" | "UPCOMING";
export interface VehicleRef { id: string; plate: string; make: string; model: string }

export interface InstallmentRequest {
  dueDate: string;
  amountCents: number;
  method: PaymentMethod;
  noteNo: string | null;
  payerId: string | null;   // bos: alici; INSTITUTION'da kurum zorunlu
  paidNow: boolean;         // pesinat: ayni islemde accountId'ye tahsil edilir
  accountId: string | null;
}

export interface InstallmentResponse {
  id: string;
  planId: string;
  direction: PlanDirection;
  vehicle: VehicleRef | null;
  seq: number;
  count: number;
  dueDate: string;
  amountCents: number;
  method: PaymentMethod;
  noteNo: string | null;
  payer: ContactSummary;
  counterparty: ContactSummary;
  status: InstallmentStatus;
  daysLate: number;
  paidEntryId: string | null;
  paidAt: string | null;
}

export interface PaymentPlanResponse {
  id: string;
  direction: PlanDirection;
  vehicleId: string;
  counterparty: ContactSummary;
  totalCents: number;
  paidCents: number;
  remainingCents: number;
  overdueCents: number;
  installments: InstallmentResponse[];
}

export interface StatementLine {
  entryId: string;
  date: string;
  type: LedgerEntryType;
  description: string | null;
  vehicle: VehicleRef | null;
  amountCents: number;    // arti: cariye giren (bize borclandi), eksi: cariden cikan
  runningCents: number;
  reversal: boolean;
}
export interface ContactStatement {
  contact: ContactResponse;
  balanceCents: number;   // arti: alacak, eksi: verecek
  lines: StatementLine[];
  openInstallments: InstallmentResponse[];
}
export interface ContactRequest { name: string; phone: string | null; type: ContactType; notes: string | null }

// ---- Para defteri (sadece ADMIN). Tutarlar kurus. ----
export type AccountType = "CASH" | "BANK" | "BALANCE" | "CAPITAL" | "VEHICLE" | "EXPENSE" | "INCOME";
export type LedgerEntryType =
  | "VEHICLE_PURCHASE" | "VEHICLE_EXPENSE" | "GENERAL_EXPENSE" | "CAPITAL" | "VEHICLE_SALE" | "COLLECTION" | "PAYMENT";
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
