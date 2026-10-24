/** Error body of a failed API call (contracts/openapi.yaml `Problem`). */
export interface ApiProblem {
  title?: string;
  status?: number;
  detail?: string;
  /** For example `STALE_VERSION`. */
  code?: string;
  retryAfterSeconds?: number;
  fieldErrors?: { field: string; message: string }[];
  /** The latest record when `code` is `STALE_VERSION` (FR-046). */
  current?: unknown;
}

export const STALE_VERSION = 'STALE_VERSION';

/** Reads the problem body from a failed response; null when the body is not a problem (for example a blob). */
export function problemOf(body: unknown): ApiProblem | null {
  return body !== null && typeof body === 'object' && !(body instanceof Blob) ? (body as ApiProblem) : null;
}
