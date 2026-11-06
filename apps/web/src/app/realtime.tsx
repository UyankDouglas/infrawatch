import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Client, type IMessage } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "@/features/auth/AuthProvider";
import type { AlertEvent, AppNotification, RealtimeEvent } from "@/types/alerts";
import type { ResourceStatus } from "@/types/api";

export type RealtimeStatus = "disabled" | "connecting" | "connected" | "reconnecting" | "offline";

interface RealtimeContextValue {
  status: RealtimeStatus;
  lastEventAt: string | null;
  eventsReceived: number;
  attempt: number;
}

const RealtimeContext = createContext<RealtimeContextValue>({
  status: "disabled",
  lastEventAt: null,
  eventsReceived: 0,
  attempt: 0,
});

const MAX_BACKOFF_MS = 30_000;

function wsUrl(): string {
  const base = (import.meta.env.VITE_API_URL ?? "") as string;
  const origin = base && base.startsWith("http") ? new URL(base) : new URL(window.location.origin);
  const proto = origin.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${origin.host}/ws`;
}

const STATUS_LABEL: Record<ResourceStatus, string> = {
  UP: "Disponível",
  DOWN: "Indisponível",
  DEGRADED: "Degradado",
  PENDING: "Aguardando verificação",
  UNKNOWN: "Sem dados",
  MAINTENANCE: "Em manutenção",
};

/**
 * Conexão STOMP/WebSocket com o backend.
 * - Autenticada pela sessão (cookie HttpOnly enviado no handshake).
 * - Assina /topic/org/{orgId} e /user/queue/notifications.
 * - Reconexão com backoff exponencial; a cada reconexão e a cada salto de sequência, ressincroniza via REST
 *   (invalida as queries). A API REST continua sendo a fonte de verdade.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const orgId = user?.currentOrganization?.id ?? null;
  const [status, setStatus] = useState<RealtimeStatus>("disabled");
  const [lastEventAt, setLastEventAt] = useState<string | null>(null);
  const [eventsReceived, setEventsReceived] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const lastSeq = useRef<number>(0);
  const connectedOnce = useRef(false);

  useEffect(() => {
    if (!orgId) {
      setStatus("disabled");
      return;
    }
    let disposed = false;
    let backoff = 1_000;
    let attempts = 0;
    lastSeq.current = 0;

    const resync = () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["alerts"] });
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    };

    const handleOrgEvent = (message: IMessage) => {
      let event: RealtimeEvent;
      try {
        event = JSON.parse(message.body) as RealtimeEvent;
      } catch {
        return;
      }
      if (event.organizationId !== orgId) return; // defesa extra: o servidor já isola por assinatura
      if (lastSeq.current && event.seq > lastSeq.current + 1) {
        // Perda de mensagens detectada: ressincroniza tudo.
        resync();
      }
      lastSeq.current = Math.max(lastSeq.current, event.seq);
      setLastEventAt(event.occurredAt);
      setEventsReceived((n) => n + 1);

      switch (event.type) {
        case "resource.status_changed": {
          const p = event.payload as {
            resourceId: string;
            resourceName: string;
            fromStatus: ResourceStatus;
            toStatus: ResourceStatus;
            reason: string;
          };
          queryClient.invalidateQueries({ queryKey: ["resources"] });
          queryClient.invalidateQueries({ queryKey: ["dashboard"] });
          const label = STATUS_LABEL[p.toStatus] ?? p.toStatus;
          const text = `${p.resourceName}: ${label}`;
          if (p.toStatus === "DOWN") toast.error(text, { description: p.reason || undefined });
          else if (p.toStatus === "DEGRADED" || p.toStatus === "UNKNOWN")
            toast.warning(text, { description: p.reason || undefined });
          else toast.success(text, { description: p.reason || undefined });
          break;
        }
        case "resource.check_completed": {
          const p = event.payload as { resourceId: string };
          queryClient.invalidateQueries({ queryKey: ["resources", "detail", p.resourceId] });
          queryClient.invalidateQueries({ queryKey: ["resources", "checks", p.resourceId] });
          queryClient.invalidateQueries({ queryKey: ["resources", "metrics", p.resourceId] });
          queryClient.invalidateQueries({ queryKey: ["resources", "list"] });
          break;
        }
        case "alert.triggered":
        case "alert.acknowledged":
        case "alert.resolved":
        case "alert.updated": {
          queryClient.invalidateQueries({ queryKey: ["alerts"] });
          queryClient.invalidateQueries({ queryKey: ["dashboard"] });
          queryClient.invalidateQueries({ queryKey: ["resources", "alerts"] });
          if (event.type === "alert.triggered") {
            const a = event.payload as AlertEvent;
            const fn = a.severity === "CRITICAL" || a.severity === "HIGH" ? toast.error : toast.warning;
            fn(`[${a.severityLabel}] ${a.title}`, { description: a.message ?? undefined });
          }
          break;
        }
        default:
          break;
      }
    };

    const handleUserEvent = (message: IMessage) => {
      try {
        const event = JSON.parse(message.body) as RealtimeEvent<AppNotification>;
        if (event.type === "notification.created") {
          queryClient.invalidateQueries({ queryKey: ["notifications"] });
        }
      } catch {
        // ignorar
      }
    };

    setStatus("connecting");
    const client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 0, // reconexão controlada manualmente (backoff exponencial)
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => {},
      onConnect: () => {
        if (disposed) return;
        attempts = 0;
        backoff = 1_000;
        setAttempt(0);
        setStatus("connected");
        client.subscribe(`/topic/org/${orgId}`, handleOrgEvent);
        client.subscribe("/user/queue/notifications", handleUserEvent);
        if (connectedOnce.current) {
          resync(); // reconectou: o que aconteceu enquanto estava fora vem da API REST
        }
        connectedOnce.current = true;
      },
      onWebSocketClose: () => {
        if (disposed) return;
        attempts += 1;
        setAttempt(attempts);
        setStatus(attempts >= 5 ? "offline" : "reconnecting");
        const delay = Math.min(MAX_BACKOFF_MS, backoff) + Math.floor(Math.random() * 500);
        backoff = Math.min(MAX_BACKOFF_MS, backoff * 2);
        window.setTimeout(() => {
          if (!disposed && !client.active) client.activate();
          else if (!disposed) client.deactivate().then(() => client.activate());
        }, delay);
      },
      onStompError: (frame) => {
        console.warn("STOMP error", frame.headers["message"]);
      },
    });
    client.activate();

    const onVisible = () => {
      if (document.visibilityState === "visible" && client.connected) resync();
    };
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      disposed = true;
      document.removeEventListener("visibilitychange", onVisible);
      client.deactivate();
    };
  }, [orgId, queryClient]);

  const value = useMemo(
    () => ({ status, lastEventAt, eventsReceived, attempt }),
    [status, lastEventAt, eventsReceived, attempt],
  );
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): RealtimeContextValue {
  return useContext(RealtimeContext);
}
