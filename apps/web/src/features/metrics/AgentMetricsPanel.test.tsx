import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/render";
import { AgentMetricsPanel } from "./AgentMetricsPanel";
import type { AgentMetrics } from "@/types/metrics";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const base: AgentMetrics = {
  resourceId: "r1",
  source: "agent",
  hasAgent: true,
  agent: {
    id: "a1",
    resourceId: "r1",
    resourceName: "Servidor",
    name: "agente-01",
    status: "ACTIVE",
    intervalSeconds: 15,
    hostname: "srv-01",
    agentVersion: "0.2.0",
    lastSeenAt: new Date().toISOString(),
    lastSampleAt: new Date().toISOString(),
    stale: false,
    tokenPrefix: "abc12345",
    createdAt: new Date().toISOString(),
  },
  stale: false,
  from: "",
  to: "",
  bucketSeconds: 30,
  granularity: "raw",
  latest: {
    sampledAt: new Date().toISOString(),
    receivedAt: new Date().toISOString(),
    cpuPercent: 42.5,
    loadAvg1m: 0.8,
    memTotalBytes: 8 * 1024 ** 3,
    memUsedBytes: 6 * 1024 ** 3,
    memPercent: 75,
    diskPath: "/",
    diskTotalBytes: 100 * 1024 ** 3,
    diskUsedBytes: 50 * 1024 ** 3,
    diskPercent: 50,
    uptimeSeconds: 90000,
  },
  values: [
    { key: "cpu", label: "Uso de CPU", value: 42.5, unit: "%", available: true, note: null },
    { key: "memory", label: "Memória utilizada", value: 75, unit: "%", available: true, note: null },
    { key: "disk", label: "Disco utilizado", value: 50, unit: "%", available: true, note: null },
    { key: "uptime", label: "Uptime do sistema", value: 90000, unit: "s", available: true, note: null },
    { key: "load", label: "Carga média (1 min)", value: 0.8, unit: "", available: true, note: null },
  ],
  missingMetrics: [],
  series: [
    {
      bucket: new Date().toISOString(),
      samples: 1,
      cpuAvg: 42.5,
      cpuMax: 42.5,
      memPercentAvg: 75,
      memPercentMax: 75,
      memUsedAvg: 1,
      diskPercentAvg: 50,
      diskPercentMax: 50,
      diskUsedAvg: 1,
    },
  ],
  note: "Métricas coletadas pelo agente autorizado.",
};

describe("AgentMetricsPanel", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("mostra CPU, memória, disco e uptime reportados pelo agente e identifica a fonte", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, base));
    renderWithProviders(<AgentMetricsPanel resourceId="r1" period="1h" onPeriodChange={() => {}} />);
    expect(await screen.findByText("42,5%")).toBeInTheDocument();
    expect(screen.getByText("75,0%")).toBeInTheDocument();
    expect(screen.getByText("50,0%")).toBeInTheDocument();
    expect(screen.getByText("1 d 1 h")).toBeInTheDocument();
    expect(screen.getByText("Fonte: agente autorizado")).toBeInTheDocument();
    expect(screen.getByText(/6,0 GB de 8,0 GB/)).toBeInTheDocument();
  });

  it("exibe métricas não reportadas como indisponíveis, sem inventar valores", async () => {
    const partial: AgentMetrics = {
      ...base,
      latest: { ...base.latest!, memPercent: null, memTotalBytes: null, memUsedBytes: null, uptimeSeconds: null },
      values: base.values.map((v) =>
        v.key === "memory" || v.key === "uptime"
          ? { ...v, value: null, available: false, note: "Métrica não reportada pelo agente" }
          : v,
      ),
      missingMetrics: ["memory", "uptime"],
    };
    fetchMock.mockResolvedValue(jsonResponse(200, partial));
    renderWithProviders(<AgentMetricsPanel resourceId="r1" period="1h" onPeriodChange={() => {}} />);
    await screen.findByText("42,5%");
    expect(screen.getAllByText("Indisponível")).toHaveLength(2);
    expect(screen.getAllByText("Métrica não reportada pelo agente")).toHaveLength(2);
    expect(screen.getByText(/Não reportadas: memory, uptime/)).toBeInTheDocument();
  });

  it("orienta a registrar um agente quando não há nenhum", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, {
        ...base,
        hasAgent: false,
        agent: null,
        latest: null,
        values: [],
        series: [],
        missingMetrics: ["cpu", "memory", "disk", "uptime"],
      }),
    );
    renderWithProviders(<AgentMetricsPanel resourceId="r1" period="1h" onPeriodChange={() => {}} />);
    expect(await screen.findByText("Nenhum agente registrado")).toBeInTheDocument();
  });

  it("sinaliza agente silencioso", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, { ...base, stale: true, note: "O agente não envia amostras há mais de 45 s." }),
    );
    renderWithProviders(<AgentMetricsPanel resourceId="r1" period="1h" onPeriodChange={() => {}} />);
    expect(await screen.findByText("Agente silencioso")).toBeInTheDocument();
  });
});
