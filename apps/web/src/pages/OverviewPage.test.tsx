import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/render";
import { OverviewPage } from "./OverviewPage";
import type { Overview } from "@/types/api";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const overview: Overview = {
  from: "2026-01-01T00:00:00Z",
  to: "2026-01-02T00:00:00Z",
  period: "24h",
  totalResources: 3,
  available: 2,
  unavailable: 1,
  degraded: 0,
  withoutData: 0,
  pendingResources: 0,
  unknownResources: 0,
  pausedResources: 0,
  activeIncidents: 0,
  activeAlerts: 0,
  availabilityPercent: 98.5,
  observedSeconds: 7200,
  availabilityNote: "nota",
  averageResponseMs: 123.4,
  checksInPeriod: 40,
  failedChecksInPeriod: 2,
  statusDistribution: [
    { status: "UP", label: "Disponível", total: 2 },
    { status: "DOWN", label: "Indisponível", total: 1 },
  ],
  resourcesByType: [{ type: "HTTP", label: "Serviço HTTP", total: 3 }],
  problematic: [
    {
      id: "r1",
      name: "API Pagamentos",
      type: "HTTP",
      typeLabel: "Serviço HTTP",
      environment: "PRODUCTION",
      environmentLabel: "Produção",
      status: "DOWN",
      statusLabel: "Indisponível",
      statusSince: new Date().toISOString(),
      lastCheckAt: new Date().toISOString(),
      lastResponseTimeMs: null,
      lastErrorCode: "timeout",
      groupName: null,
    },
  ],
  resources: [],
  recentTransitions: [],
  recentAlerts: [],
};

describe("OverviewPage", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("mostra indicadores reais vindos da API e os recursos com problema", async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes("/dashboard/overview")) return jsonResponse(200, overview);
      if (url.includes("/dashboard/availability"))
        return jsonResponse(200, { from: "", to: "", bucketSeconds: 900, points: [], note: "" });
      if (url.includes("/dashboard/latency"))
        return jsonResponse(200, { from: "", to: "", bucketSeconds: 900, series: [] });
      return jsonResponse(404, {});
    });
    renderWithProviders(<OverviewPage />);

    expect(await screen.findByText("98,50%")).toBeInTheDocument();
    expect(screen.getByText("123 ms")).toBeInTheDocument();
    expect(screen.getByText("API Pagamentos")).toBeInTheDocument();
    expect(screen.getByText("Sem verificações no período")).toBeInTheDocument();
  });

  it("mostra estado vazio quando não há recursos", async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes("/dashboard/overview"))
        return jsonResponse(200, { ...overview, totalResources: 0, available: 0, unavailable: 0, problematic: [] });
      return jsonResponse(200, { points: [], series: [], bucketSeconds: 900, from: "", to: "", note: "" });
    });
    renderWithProviders(<OverviewPage />);
    expect(await screen.findByText("Nenhum recurso monitorado ainda")).toBeInTheDocument();
  });

  it("informa quando não há dados suficientes para a disponibilidade", async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes("/dashboard/overview"))
        return jsonResponse(200, {
          ...overview,
          availabilityPercent: null,
          averageResponseMs: null,
          observedSeconds: 0,
        });
      return jsonResponse(200, { points: [], series: [], bucketSeconds: 900, from: "", to: "", note: "" });
    });
    renderWithProviders(<OverviewPage />);
    await screen.findByText("API Pagamentos");
    // título do card "Sem dados suficientes" + disponibilidade + tempo médio de resposta
    expect(screen.getAllByText("Sem dados suficientes").length).toBeGreaterThanOrEqual(3);
  });
});
