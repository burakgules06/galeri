export class ApiError extends Error {
  readonly status: number;
  readonly code: number | null;

  constructor(status: number, message: string, code: number | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}
