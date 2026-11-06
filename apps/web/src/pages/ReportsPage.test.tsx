import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/render";
import { ReportsPage } from "./ReportsPage";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const availability = {
  from: "2026-01-01T00:00:00Z",
  to: "2026-01-08T00:00:00Z",
  period: "7d",
  maintenanceMode: "excluded",
  formula:
    "Disponibilidade (%) = tempo disponível ÷ tempo observado × 100. Períodos em manutenção EXCLUÍDOS do tempo observado.",
  overallAvailabilityPercent: 99.5,
  totalIncidents: 1,
  overallMttrSeconds: 600,
  totalUnavailableSeconds: 900,
  rows: [
    {
      resourceId: "r1",
      name: "API Pagamentos",
      type: "HTTP",
      typeLabel: "Serviço HTTP",
      environment: "PRODUCTION",
      environmentLabel: "Produção",
      groupName: "Core",
      currentStatus: "UP",
      currentStatusLabel: "Disponível",
      availabilityPercent: 99.5,
      observedSeconds: 180000,
      availableSeconds: 179100,
      unavailableSeconds: 900,
      maintenanceSeconds: 0,
      checks: 120,
      failedChecks: 3,
      avgResponseMs: 120.5,
      maxResponseMs: 800,
      p95ResponseMs: 300,
      incidents: 1,
      mttrSeconds: 600,
      mtbfSeconds: null,
      mtbfNote: "MTBF exige ao menos 2 incidentes no período",
    },
    {
      resourceId: "r2",
      name: "Servidor sem dados",
      type: "AGENT_SERVER",
      typeLabel: "Servidor (agente)",
      environment: "DEVELOPMENT",
      environmentLabel: "Desenvolvimento",
      groupName: null,
      currentStatus: "PENDING",
      currentStatusLabel: "Aguardando",
      availabilityPercent: null,
      observedSeconds: 0,
      availableSeconds: 0,
      unavailableSeconds: 0,
      maintenanceSeconds: 0,
      checks: 0,
      failedChecks: 0,
      avgResponseMs: null,
      maxResponseMs: null,
      p95ResponseMs: null,
      incidents: 0,
      mttrSeconds: null,
      mtbfSeconds: null,
      mtbfNote: "MTBF exige ao menos 2 incidentes no período",
    },
  ],
};

const incidents = {
  from: "2026-01-01T00:00:00Z",
  to: "2026-01-08T00:00:00Z",
  period: "7d",
  total: 1,
  automatic: 1,
  manual: 0,
  bySeverityCritical: 1,
  bySeverityHigh: 0,
  bySeverityWarning: 0,
  bySeverityInfo: 0,
  mttrSeconds: 600,
  mttaSeconds: 120,
  note: "MTTR = média de (resolvido − aberto)",
  rows: [
    {
      id: "i1",
      number: 3,
      title: "Indisponibilidade: API Pagamentos",
      severity: "CRITICAL",
      severityLabel: "Crítico",
      status: "RESOLVED",
      statusLabel: "Resolvido",
      origin: "AUTOMATIC",
      originLabel: "Automático",
      resourceId: "r1",
      resourceName: "API Pagamentos",
      assignee: null,
      openedAt: "2026-01-03T10:00:00Z",
      acknowledgedAt: "2026-01-03T10:02:00Z",
      resolvedAt: "2026-01-03T10:10:00Z",
      durationSeconds: 600,
      timeToAcknowledgeSeconds: 120,
      cause: "Deploy com falha",
    },
  ],
};

describe("ReportsPage", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=abc; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("mostra indicadores, a fórmula com o tratamento de manutenções, 'sem dados' em vez de 100% e gera exportação", async () => {
    const posted: unknown[] = [];
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.includes("/reports/availability")) return jsonResponse(200, availability);
      if (url.includes("/reports/incidents")) return jsonResponse(200, incidents);
      if (url.includes("/reports/exports") && init?.method === "POST") {
        posted.push(JSON.parse(String(init.body)));
        return jsonResponse(201, {
          id: "e1",
          type: "AVAILABILITY",
          typeLabel: "Disponibilidade",
          format: "CSV",
          period: "7d",
          maintenanceMode: "excluded",
          fileName: "infrawatch-availability-7d.csv",
          contentType: "text/csv",
          sizeBytes: 512,
          rowCount: 2,
          createdBy: "Admin",
          createdAt: "2026-01-08T00:00:00Z",
        });
      }
      if (url.includes("/reports/exports")) return jsonResponse(200, []);
      if (url.includes("/resource-groups")) return jsonResponse(200, []);
      return jsonResponse(404, {});
    });

    renderWithProviders(<ReportsPage />, { route: "/relatorios" });

    expect((await screen.findAllByText("99,50%")).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText(/EXCLUÍDOS do tempo observado/)).toBeInTheDocument();
    const table = screen.getByRole("table");
    expect(within(table).getByText("API Pagamentos")).toBeInTheDocument();
    expect(within(table).getByText("sem dados")).toBeInTheDocument();
    expect(within(table).getByText(/3 falhas/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Exportar disponibilidade/ }));
    await waitFor(() =>
      expect(posted).toEqual([{ type: "AVAILABILITY", format: "CSV", period: "7d", maintenance: "excluded" }]),
    );
  });

  it("aba de incidentes lista o relatório com MTTR e causa", async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes("/reports/availability")) return jsonResponse(200, availability);
      if (url.includes("/reports/incidents")) return jsonResponse(200, incidents);
      if (url.includes("/reports/exports")) return jsonResponse(200, []);
      if (url.includes("/resource-groups")) return jsonResponse(200, []);
      return jsonResponse(404, {});
    });
    renderWithProviders(<ReportsPage />, { route: "/relatorios" });
    await screen.findAllByText("99,50%");
    await userEvent.click(screen.getByRole("tab", { name: "Incidentes" }));
    const panel = await screen.findByRole("tabpanel");
    expect(within(panel).getByText("Indisponibilidade: API Pagamentos")).toBeInTheDocument();
    expect(within(panel).getByText(/Causa: Deploy com falha/)).toBeInTheDocument();
    expect(within(panel).getByText(/MTTR = média/)).toBeInTheDocument();
  });
});
