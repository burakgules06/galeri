export class ApiError extends Error {
  readonly status: number;
  readonly code: number | null;
  /** Hata cevabinin data alani (or. parasi yetmeyen kisi ve eksik tutar); cogu hatada null */
  readonly data: unknown;

  constructor(status: number, message: string, code: number | null = null, data: unknown = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.data = data;
  }
}
