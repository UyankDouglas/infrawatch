import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/render";
import { AuthProvider } from "@/features/auth/AuthProvider";
import { IncidentsPage } from "./IncidentsPage";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const me = {
  id: "u1",
  email: "op@demo.local",
  fullName: "Otávio",
  createdAt: "2026-01-01T00:00:00Z",
  currentOrganization: { id: "o1", name: "Demo", slug: "demo", role: "OPERATOR", roleLabel: "Operador" },
  organizations: [],
};

describe("IncidentsPage", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("mostra indicadores, MTTR indisponível sem dados e a lista de incidentes com origem e status", async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith("/auth/me")) return jsonResponse(200, me);
      if (url.includes("/incidents/summary"))
        return jsonResponse(200, {
          open: 1,
          acknowledged: 0,
          investigating: 1,
          resolvedLast24h: 0,
          critical: 1,
          high: 0,
          mttrSeconds: null,
          mttrNote: "sem dados",
        });
      if (url.includes("/incidents"))
        return jsonResponse(200, {
          items: [
            {
              id: "i1",
              number: 7,
              title: "Indisponibilidade: API Pagamentos",
              description: null,
              severity: "CRITICAL",
              severityLabel: "Crítico",
              status: "OPEN",
              statusLabel: "Aberto",
              origin: "AUTOMATIC",
              originLabel: "Automático",
              resourceId: "r1",
              resourceName: "API Pagamentos",
              alertEventId: "a1",
              assignee: null,
              createdBy: null,
              openedAt: new Date().toISOString(),
              acknowledgedAt: null,
              resolvedAt: null,
              closedAt: null,
              durationSeconds: 125,
              cause: null,
              notes: null,
              commentCount: 0,
              updatedAt: new Date().toISOString(),
            },
            {
              id: "i2",
              number: 6,
              title: "Lentidão no checkout",
              description: null,
              severity: "HIGH",
              severityLabel: "Alto",
              status: "INVESTIGATING",
              statusLabel: "Em investigação",
              origin: "MANUAL",
              originLabel: "Manual",
              resourceId: null,
              resourceName: null,
              alertEventId: null,
              assignee: { id: "u1", name: "Otávio" },
              createdBy: { id: "u1", name: "Otávio" },
              openedAt: new Date().toISOString(),
              acknowledgedAt: new Date().toISOString(),
              resolvedAt: null,
              closedAt: null,
              durationSeconds: 3600,
              cause: null,
              notes: null,
              commentCount: 2,
              updatedAt: new Date().toISOString(),
            },
          ],
          page: 0,
          size: 20,
          totalItems: 2,
          totalPages: 1,
        });
      return jsonResponse(404, {});
    });
    renderWithProviders(
      <AuthProvider>
        <IncidentsPage />
      </AuthProvider>,
    );
    expect(await screen.findByText("Indisponibilidade: API Pagamentos")).toBeInTheDocument();
    expect(screen.getByText("Lentidão no checkout")).toBeInTheDocument();
    expect(screen.getAllByText("Em investigação").length).toBeGreaterThanOrEqual(2); // KPI + badge
    expect(screen.getByText(/Manual · 2 comentário/)).toBeInTheDocument();
    expect(screen.getAllByText("Sem dados suficientes").length).toBeGreaterThanOrEqual(1);
    expect(await screen.findByRole("button", { name: /Abrir incidente/ })).toBeInTheDocument();
  });
});
