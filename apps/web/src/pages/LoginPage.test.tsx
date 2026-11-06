import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router";
import { renderWithProviders } from "@/test/render";
import { AuthProvider } from "@/features/auth/AuthProvider";
import { LoginPage } from "./LoginPage";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const me = {
  id: "u1",
  email: "ana@demo.local",
  fullName: "Ana",
  createdAt: "2026-01-01T00:00:00Z",
  currentOrganization: { id: "o1", name: "Demo", slug: "demo", role: "ADMIN", roleLabel: "Administrador" },
  organizations: [],
};

describe("LoginPage", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  function setup() {
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith("/auth/me") && (!init?.method || init.method === "GET")) return jsonResponse(401, { code: "unauthorized" });
      if (url.endsWith("/auth/login")) {
        const body = JSON.parse(String(init?.body));
        return body.password === "InfraWatch@2026"
          ? jsonResponse(200, me)
          : jsonResponse(401, { code: "invalid_credentials", detail: "Credenciais inválidas." });
      }
      return jsonResponse(404, {});
    });
    return renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<h1>Visão Geral</h1>} />
        </Routes>
      </AuthProvider>,
      { route: "/login" },
    );
  }

  it("valida campos obrigatórios antes de chamar a API", async () => {
    setup();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Entrar" }));
    expect(await screen.findByText("Informe um e-mail válido.")).toBeInTheDocument();
    expect(screen.getByText("Informe a senha.")).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([u]) => String(u).endsWith("/auth/login"))).toBe(false);
  });

  it("exibe erro amigável para credenciais inválidas", async () => {
    setup();
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText("E-mail"), "ana@demo.local");
    await user.type(screen.getByLabelText("Senha"), "senha-errada");
    await user.click(screen.getByRole("button", { name: "Entrar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("E-mail ou senha incorretos.");
  });

  it("autentica e navega para a Visão Geral", async () => {
    setup();
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText("E-mail"), "ana@demo.local");
    await user.type(screen.getByLabelText("Senha"), "InfraWatch@2026");
    await user.click(screen.getByRole("button", { name: "Entrar" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Visão Geral" })).toBeInTheDocument());
  });
});
