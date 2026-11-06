import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/render";
import { IntegrationsTab } from "./IntegrationsTab";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const channels = [
  {
    id: "c1",
    name: "Hook do NOC",
    type: "WEBHOOK",
    enabled: true,
    emailRecipients: [],
    webhookUrl: "http://host.docker.internal:9101/__webhook",
    hasSecret: true,
    minSeverity: "WARNING",
    minSeverityLabel: "Atenção",
    eventTypes: [],
    lastDeliveryAt: "2026-01-08T10:00:00Z",
    lastError: null,
    createdAt: "2026-01-01T00:00:00Z",
    dispatchEnabled: false,
  },
];

const deliveries = {
  items: [
    {
      id: "d1",
      channelId: "c1",
      channelName: "Hook do NOC",
      eventType: "RESOURCE_DOWN",
      severity: "CRITICAL",
      title: "API fora do ar",
      status: "SKIPPED",
      attempts: 0,
      nextAttemptAt: null,
      lastError: "Envio externo desativado",
      sentAt: null,
      responseCode: null,
      createdAt: "2026-01-08T10:00:00Z",
    },
  ],
  page: 0,
  size: 25,
  totalItems: 1,
  totalPages: 1,
};

describe("IntegrationsTab", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=abc; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("lista canais, avisa que o envio externo está desativado e mostra o outbox; teste do canal informa o motivo", async () => {
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.includes("/integrations/channels/c1/test") && init?.method === "POST")
        return jsonResponse(200, {
          ...deliveries.items[0],
          id: "d2",
          eventType: "test",
          lastError: "Envio externo desativado (dispatch-enabled=false)",
        });
      if (url.includes("/integrations/channels")) return jsonResponse(200, channels);
      if (url.includes("/integrations/deliveries")) return jsonResponse(200, deliveries);
      return jsonResponse(404, {});
    });
    renderWithProviders(<IntegrationsTab />);

    expect((await screen.findAllByText("Hook do NOC")).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText(/envio externo está desativado/)).toBeInTheDocument();
    expect(screen.getByText("assinado")).toBeInTheDocument();
    expect(screen.getByText("API fora do ar")).toBeInTheDocument();
    expect(screen.getByText("Ignorada")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Testar/ }));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([input, init]) => String(input).includes("/channels/c1/test") && init?.method === "POST",
        ),
      ).toBe(true),
    );
  });

  it("abre o diálogo de novo canal e envia o webhook com severidade mínima", async () => {
    const posted: unknown[] = [];
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith("/integrations/channels") && init?.method === "POST") {
        posted.push(JSON.parse(String(init.body)));
        return jsonResponse(201, { ...channels[0], id: "c2", name: "Novo" });
      }
      if (url.includes("/integrations/channels/options"))
        return jsonResponse(200, { eventTypes: [{ value: "RESOURCE_DOWN", label: "Recurso indisponível" }] });
      if (url.includes("/integrations/channels")) return jsonResponse(200, []);
      if (url.includes("/integrations/deliveries"))
        return jsonResponse(200, { ...deliveries, items: [], totalItems: 0 });
      return jsonResponse(404, {});
    });
    renderWithProviders(<IntegrationsTab />);
    await screen.findByText("Nenhum canal configurado");
    await userEvent.click(screen.getByRole("button", { name: /Novo canal/ }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(screen.getByLabelText("Nome"), "Novo");
    await userEvent.type(screen.getByLabelText("URL do webhook"), "http://localhost:9101/__webhook");
    await userEvent.click(screen.getByRole("checkbox", { name: /Recurso indisponível/ }));
    await userEvent.click(screen.getByRole("button", { name: "Criar canal" }));
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
    expect(posted).toEqual([
      {
        name: "Novo",
        type: "WEBHOOK",
        enabled: true,
        minSeverity: "WARNING",
        eventTypes: ["RESOURCE_DOWN"],
        webhookUrl: "http://localhost:9101/__webhook",
      },
    ]);
  });
});
