import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/render";
import { NotificationsBell } from "./NotificationsBell";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("NotificationsBell", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("mostra o contador de não lidas e a lista ao abrir; marcar todas zera o contador", async () => {
    let unread = 2;
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.includes("/notifications/unread-count")) return jsonResponse(200, { unread });
      if (url.includes("/notifications/read-all")) {
        unread = 0;
        return jsonResponse(200, { updated: 2 });
      }
      if (url.includes("/notifications") && (!init?.method || init.method === "GET"))
        return jsonResponse(200, {
          items: [
            {
              id: "n1",
              type: "RESOURCE_DOWN",
              typeLabel: "Recurso indisponível",
              severity: "CRITICAL",
              severityLabel: "Crítico",
              title: "Recurso indisponível: API Pagamentos",
              message: "3 falhas",
              link: "/recursos/r1",
              resourceId: "r1",
              alertEventId: null,
              readAt: null,
              createdAt: new Date().toISOString(),
            },
            {
              id: "n2",
              type: "ALERT_RESOLVED",
              typeLabel: "Alerta resolvido",
              severity: "INFO",
              severityLabel: "Informativo",
              title: "Resolvido: Latência alta",
              message: null,
              link: null,
              resourceId: null,
              alertEventId: "a1",
              readAt: new Date().toISOString(),
              createdAt: new Date().toISOString(),
            },
          ],
          page: 0,
          size: 15,
          totalItems: 2,
          totalPages: 1,
        });
      return jsonResponse(404, {});
    });
    renderWithProviders(<NotificationsBell />);
    expect(await screen.findByTestId("unread-badge")).toHaveTextContent("2");

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /notificações não lidas/ }));
    expect(await screen.findByText("Recurso indisponível: API Pagamentos")).toBeInTheDocument();
    expect(screen.getByText("Resolvido: Latência alta")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Marcar todas como lidas/ }));
    await vi.waitFor(() => expect(screen.queryByTestId("unread-badge")).not.toBeInTheDocument());
  });
});
