import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "./api";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("api client", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("envia GET com credenciais e converte JSON", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { ok: true }));
    const result = await api.get<{ ok: boolean }>("/auth/me");
    expect(result).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe("/api/v1/auth/me");
    expect(init?.credentials).toBe("include");
  });

  it("obtém o cookie CSRF antes de uma mutation e envia o cabeçalho", async () => {
    fetchMock.mockImplementationOnce(async () => {
      document.cookie = "XSRF-TOKEN=token-abc; path=/";
      return jsonResponse(200, { headerName: "X-XSRF-TOKEN", cookieName: "XSRF-TOKEN" });
    });
    fetchMock.mockResolvedValueOnce(jsonResponse(201, { id: "1" }));

    await api.post("/resources", { name: "x" });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(String(fetchMock.mock.calls[0]![0])).toContain("/auth/csrf");
    const init = fetchMock.mock.calls[1]![1]!;
    expect((init.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("token-abc");
    expect(init.method).toBe("POST");
  });

  it("converte ProblemDetail em ApiError com código e erros de campo", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: "validation_error", detail: "Dados inválidos.", errors: { name: "Informe o nome." } }),
    );
    await expect(api.get("/resources")).rejects.toMatchObject<Partial<ApiError>>({
      status: 400,
      code: "validation_error",
      message: "Dados inválidos.",
      errors: { name: "Informe o nome." },
    });
  });

  it("renova o token e repete uma vez quando o CSRF é recusado", async () => {
    document.cookie = "XSRF-TOKEN=velho; path=/";
    fetchMock.mockResolvedValueOnce(jsonResponse(403, { code: "csrf_invalid" }));
    fetchMock.mockImplementationOnce(async () => {
      document.cookie = "XSRF-TOKEN=novo; path=/";
      return jsonResponse(200, {});
    });
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.post("/auth/logout");

    expect(fetchMock).toHaveBeenCalledTimes(3);
    const retryInit = fetchMock.mock.calls[2]![1]!;
    expect((retryInit.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("novo");
  });

  it("omite parâmetros vazios da query string", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { items: [] }));
    await api.get("/resources", { search: "", status: "DOWN", page: 0, groupId: undefined });
    expect(String(fetchMock.mock.calls[0]![0])).toBe("/api/v1/resources?status=DOWN&page=0");
  });
});
