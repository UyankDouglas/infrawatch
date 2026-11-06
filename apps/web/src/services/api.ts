/**
 * Cliente HTTP do InfraWatch.
 * - Cookies de sessão HttpOnly (credentials: include)
 * - CSRF "double submit": lê o cookie XSRF-TOKEN e envia em X-XSRF-TOKEN
 * - Erros no formato RFC 9457 (ProblemDetail) convertidos em ApiError
 */
const BASE_URL = (import.meta.env.VITE_API_URL ?? "") + "/api/v1";

export class ApiError extends Error {
  status: number;
  code: string;
  errors?: Record<string, string>;

  constructor(status: number, code: string, message: string, errors?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

type Query = Record<string, string | number | boolean | null | undefined>;

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Query;
  signal?: AbortSignal;
}

function readCookie(name: string): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith(name + "="));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : null;
}

async function ensureCsrf(): Promise<string | null> {
  const existing = readCookie("XSRF-TOKEN");
  if (existing) return existing;
  await fetch(`${BASE_URL}/auth/csrf`, { credentials: "include" });
  return readCookie("XSRF-TOKEN");
}

function buildUrl(path: string, query?: Query): string {
  const url = BASE_URL + path;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

async function parseError(response: Response): Promise<ApiError> {
  let code = "http_" + response.status;
  let message = `Erro ${response.status}`;
  let errors: Record<string, string> | undefined;
  try {
    const body = await response.json();
    code = body.code ?? body.title ?? code;
    message = body.detail ?? body.title ?? message;
    errors = body.errors;
  } catch {
    // corpo vazio ou não JSON
  }
  return new ApiError(response.status, code, message, errors);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    const token = await ensureCsrf();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }

  const doFetch = () =>
    fetch(buildUrl(path, options.query), {
      method,
      headers,
      credentials: "include",
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });

  let response = await doFetch();

  // Token CSRF expirado/rotacionado: renova uma vez e repete.
  if (response.status === 403 && method !== "GET") {
    const cloned = response.clone();
    const body = await cloned.json().catch(() => null);
    if (body?.code === "csrf_invalid") {
      document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
      const token = await ensureCsrf();
      if (token) headers["X-XSRF-TOKEN"] = token;
      response = await doFetch();
    }
  }

  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, query?: Query, signal?: AbortSignal) => request<T>(path, { query, signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: "PATCH", body }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};
