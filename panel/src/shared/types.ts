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
// Sahiplik: galeri (varsayilan), konsinye (sahibi bir kisi, galeri komisyon alir), ortak (Havuz ve/veya ortaklar).
export type Ownership = "DEALER" | "CONSIGNMENT" | "CO_OWNED";
export type CommissionType = "RATE" | "FIXED";   // RATE: baz puan (%3 = 300), FIXED: kurus
// Ortak aracta katkinin kaynagi: sermayeden (nakitteki sermaye araca baglanir), cepten (otomatik sermaye girisi), hibrit.
export type ContributionSource = "CAPITAL" | "POCKET" | "HYBRID";
/** Ortak aracin sahibi. contactId null = Havuz. profitShareBp null = sahiplik payi. HYBRID'de pocketCents cepten kisim. */
export interface ShareRequest {
  contactId: string | null; shareBp: number; profitShareBp: number | null; source: ContributionSource | null; pocketCents: number | null;
}
/** contributionCents: odenmis katki. Satilmis aracta profitCents kar payi (dukkan payi dusulmus), receivableCents = katki + kar payi. */
export interface ShareResponse {
  contactId: string | null; name: string; shareBp: number; profitShareBp: number; source: ContributionSource; pocketCents: number | null;
  contributionCents: number; profitCents: number | null; receivableCents: number | null;
}
// Ortak arac satisinda ortagin alacagi: sermayede kalsin / nakit ode (tamami) / hibrit (amountCents kadari nakit)
export type OwnerReturnMode = "KEEP" | "CASH" | "HYBRID";
export interface OwnerReturnRequest { contactId: string; mode: OwnerReturnMode; amountCents: number | null; accountId: string | null }

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
  ownership: Ownership;
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
  ownership: Ownership;
}

export interface ContactSummary { id: string; name: string; phone: string | null }
export interface StaffSummary { id: string; name: string | null }

/** Marka / model katalogu: custom = galeriye ozel ekleme ("Listede yok" ile eklendi) */
export interface CatalogItem { name: string; custom: boolean }

// Kullanicilar ekrani (sadece ADMIN). self: oturumdaki kullanicinin kendisi.
export type UserRole = "ADMIN" | "STAFF";
export interface UserResponse { id: string; fullName: string | null; phone: string; role: UserRole; active: boolean; self: boolean }

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
  consignor: ContactSummary | null;      // konsinye: aracin sahibi
  commissionType: CommissionType | null;
  commissionRateBp: number | null;
  commissionCents: number | null;
  shares: ShareResponse[];               // ortak arac: sahipler (Havuz dahil), paylari ve katkilari
  shopCutType: CommissionType | null;    // ortak arac: istege bagli dukkan payi
  shopCutRateBp: number | null;
  shopCutCents: number | null;
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
export interface MonthStat { year: number; month: number; bought: number; sold: number; revenueCents: number; profitCents: number; commissionCents: number }
export interface DashboardResponse {
  cashCents: number;
  bankCents: number;
  capitalCents: number;
  receivableCents: number;
  payableCents: number;
  partners: PartnerShare[];
  receivables: ContactBalance[];
  payables: ContactBalance[];
  inStockCount: number;           // konsinye haric
  consignmentCount: number;       // stoktaki konsinye araclar; stok maliyetine girmez
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
  purchaseAccountId: string | null;                // pesin alis
  purchasePlan: InstallmentRequest[] | null;       // senetli / taksitli alis (odenmis taksitler haric)
  ownership: Ownership;
  consignorId: string | null;
  commissionType: CommissionType | null;
  commissionRateBp: number | null;
  commissionCents: number | null;
  shares: ShareRequest[] | null;
  addToCatalog: boolean | null;   // "Listede yok": once galerinin katalogune eklenir
  catalogModel: string | null;    // kataloga eklenecek temel model (paketsiz)
  shopCutType: CommissionType | null;   // ortak arac: dukkan payi (ortaklarin kar payindan havuza)
  shopCutRateBp: number | null;
  shopCutCents: number | null;
}

export interface SaleRequest {
  saleDate: string;
  buyerId: string;
  soldById: string | null;
  salePriceCents: number;
  installments: InstallmentRequest[];   // toplami salePriceCents'e kurusu kurusuna esit
  ownerReturns: OwnerReturnRequest[] | null;   // ortak arac: ortaga donus (bos = sermayede kalir)
}

// ---- Odeme plani ve taksitler (sadece ADMIN). Tutarlar kurus. ----
export type PlanDirection = "SALE" | "PURCHASE";
export type PaymentMethod = "CASH" | "TRANSFER" | "PROMISSORY_NOTE" | "INSTITUTION" | "CHEQUE";
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
  bankName?: string | null;     // cek
  drawerName?: string | null;   // cek: keside eden
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
  bankName: string | null;
  drawerName: string | null;
  endorsedTo: ContactSummary | null;   // ciro edildiyse cekin / senedin verildigi kisi
  bounced: boolean;                     // karsiliksiz cikti, hala acik
  downPayment: boolean;                 // plan kurulurken alinan / odenen pesinat
  number: number;                       // pesinat haric taksit no (pesinatta 0)
  installmentCount: number;             // pesinat haric taksit sayisi
}

// Toplu satis: ayni aliciya birden fazla arac, toplam uzerinden tek plan.
export interface BasketSaleRequest {
  saleDate: string;
  buyerId: string;
  soldById: string | null;
  items: { vehicleId: string; salePriceCents: number }[];
  installments: InstallmentRequest[];
}

// Gecmis senet / cek aktarimi (aracsiz plan). SALE: kisi bize borclu, PURCHASE: biz borcluyuz.
export interface OpenItemsRequest { direction: PlanDirection; date: string; installments: InstallmentRequest[] }

export interface PaymentPlanResponse {
  id: string;
  direction: PlanDirection;
  vehicleId: string | null;   // bos: gecmis senet / cek (aracsiz)
  counterparty: ContactSummary;
  totalCents: number;
  paidCents: number;
  remainingCents: number;
  overdueCents: number;
  installments: InstallmentResponse[];
  basket: { vehicle: VehicleRef; salePriceCents: number }[];   // toplu satista sepetteki araclar (normal planda bos)
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
export type AccountType = "CASH" | "BANK" | "BALANCE" | "CAPITAL" | "VEHICLE" | "EXPENSE" | "INCOME" | "OPENING";
export type LedgerEntryType =
  | "VEHICLE_PURCHASE" | "VEHICLE_EXPENSE" | "GENERAL_EXPENSE" | "CAPITAL" | "VEHICLE_SALE" | "COLLECTION" | "PAYMENT"
  | "CONSIGNMENT_SETTLEMENT" | "PROFIT_ALLOCATION" | "TRANSFER" | "OPENING_BALANCE" | "VEHICLE_PROFIT_SHARE" | "CAPITAL_TRANSFER";
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

// CONSIGNMENT: purchase 0, expenseCents = galerinin ustlendigi masraf, kar = komisyon - expenseCents.
export interface VehicleCost {
  vehicleId: string;
  ownership: Ownership;
  purchaseCents: number;
  purchaseInLedger: boolean;
  expenseCents: number;
  totalCents: number;
  salePriceCents: number | null;
  profitCents: number | null;
  commissionCents: number | null;
  ownerPayoutCents: number | null;   // konsinye: sahibine borc (satis - komisyon)
  ownerChargedCents: number;         // konsinye: sahibine yansitilan masraf
  shares: ShareResponse[];           // ortak arac: sahipler, katkilar, kar paylari
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
  chargeToOwner: boolean | null;   // konsinye aracta: masraf sahibinin carisine
}

export interface ContactPaymentRequest { accountId: string; date: string; amountCents: number; description: string | null }

// ---- Sermaye, donem kari, kasa islemleri (sadece ADMIN). Tutarlar kurus, paylar baz puan. ----
// Sermaye CAPITAL hareketlerinden hesaplanir, hicbir yerde saklanmaz. Sermaye = nakitteki + araclarda bagli (ortak araclar).
export interface Partner {
  contactId: string; name: string; phone: string | null; owner: boolean; capitalCents: number;
  cashCents: number; boundCents: number; shareBp: number;
}
export interface NetWorthOwner {
  contactId: string | null; name: string; shareBp: number; assetCents: number; debtCents: number;
  netCents: number; contributionCents: number;
}
export interface NetWorthResponse {
  cashCents: number; bankCents: number; stockCents: number; receivableCents: number; payableCents: number;
  totalCents: number; capitalCents: number; openingCents: number; undistributedCents: number; differenceCents: number;
  partners: Partner[];
  vehicles: { vehicle: VehicleRef; costCents: number; debtCents: number; owners: NetWorthOwner[] }[];
}
// Havuz modeli: sermayedarin sermayesi defterden, dukkan sahibininki havuzdan kalan. Yuzde sadece bilgi (tabana gore).
export type CapitalBase = "ALL" | "VEHICLES" | "CASH";
export interface Holder {
  contactId: string | null; name: string; owner: boolean; base: CapitalBase; capitalCents: number; cashCents: number;
  vehicleCents: number; otherCents: number; shareBp: number; vehicles: { vehicle: VehicleRef; cents: number }[];
}
export interface CapitalTransferRequest { fromContactId: string; toContactId: string; date: string; amountCents: number; description: string | null }
export type CapitalDirection = "IN" | "OUT";
export interface CapitalMovementRequest { direction: CapitalDirection; accountId: string; date: string; amountCents: number; description: string | null }
export interface ProfitAllocationRequest { contactId: string; date: string; amountCents: number; periodFrom: string; periodTo: string }
export interface TransferRequest { fromAccountId: string; toAccountId: string; date: string; amountCents: number; description: string | null }
export type OpeningSide = "RECEIVABLE" | "PAYABLE";
export interface OpeningBalanceRequest { accountId: string | null; contactId: string | null; side: OpeningSide | null; date: string; amountCents: number }

/**
 * CONSIGNMENT'ta profitCents komisyondur (costCents null). poolProfitCents havuza kalan: ortak aracta havuz payi + dukkan payi
 * (ortaklarin payi satista sermayelerine gecti).
 */
export interface SoldVehicleProfit {
  vehicle: VehicleRef; ownership: Ownership; saleDate: string; salePriceCents: number; costCents: number | null; profitCents: number;
  poolProfitCents: number;
}
export interface PartnerProfit {
  contactId: string; name: string; owner: boolean;
  capitalCents: number;     // agirlik: donem sonu nakitteki sermaye (araclarda bagli kisim haric)
  shareBp: number;
  suggestedCents: number;   // net kar x pay (sadece bilgi)
  allocatedCents: number;   // donem icinde sermayeye eklenen
}
/** Havuzun kari: galeri araclari + ortak araclarda havuz payi + komisyon - genel giderler. coOwned: ortaklara yazilan paylar (nete girmez). */
export interface PeriodProfit {
  from: string; to: string; vehicles: SoldVehicleProfit[];
  vehicleProfitCents: number; commissionCents: number; generalExpenseCents: number; netCents: number;
  coOwnedProfitCents: number; partners: PartnerProfit[];
}
