// MakeModelPicker'in ust forma verdigi deger (component dosyasi sadece component export etsin diye ayri).
export interface MakeModelValue {
  make: string;
  /** "temel model + paket": "Passat 1.5 TSI Business" */
  model: string;
  /** "Listede yok": true ise kayitta once galerinin katalogune eklenir */
  addToCatalog: boolean | null;
  /** Kataloga eklenecek temel model (paketsiz) */
  catalogModel: string | null;
  /** Marka ve temel model secildi mi */
  ready: boolean;
  /** "Listede yok" secildi ama kataloga ekleme onaylanmadi: kaydedilemez */
  needsCatalogConsent: boolean;
}
