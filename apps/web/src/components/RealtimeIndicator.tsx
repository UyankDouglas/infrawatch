import { Radio, WifiOff } from "lucide-react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useRealtime, type RealtimeStatus } from "@/app/realtime";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";

const META: Record<RealtimeStatus, { label: string; dot: string; description: string }> = {
  disabled: { label: "Tempo real inativo", dot: "bg-muted-foreground", description: "Sem organização ativa." },
  connecting: { label: "Conectando…", dot: "bg-status-degraded", description: "Abrindo conexão WebSocket." },
  connected: {
    label: "Tempo real",
    dot: "bg-status-up",
    description: "Eventos chegam pelo WebSocket; a API REST continua sendo a fonte de verdade.",
  },
  reconnecting: {
    label: "Reconectando…",
    dot: "bg-status-degraded",
    description:
      "Conexão perdida. Tentando novamente com espera progressiva; enquanto isso a interface usa consultas periódicas.",
  },
  offline: {
    label: "Sem tempo real",
    dot: "bg-status-down",
    description: "Não foi possível manter a conexão. Os dados continuam sendo atualizados por consultas periódicas.",
  },
};

export function RealtimeIndicator() {
  const { status, lastEventAt, eventsReceived, attempt } = useRealtime();
  const meta = META[status];
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          className="hidden items-center gap-2 rounded-md border border-border px-2 py-1 text-xs text-muted-foreground sm:inline-flex"
          aria-label={meta.label}
          data-testid="realtime-indicator"
          data-status={status}
        >
          {status === "offline" ? (
            <WifiOff className="size-3.5" aria-hidden />
          ) : (
            <Radio className="size-3.5" aria-hidden />
          )}
          <span
            className={cn("status-dot size-2", meta.dot, status === "connected" && "status-dot-pulse")}
            aria-hidden
          />
          <span className="hidden lg:inline">{meta.label}</span>
        </button>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs">
        <p className="font-medium">{meta.label}</p>
        <p className="text-xs opacity-80">{meta.description}</p>
        {status === "connected" && (
          <p className="mt-1 text-xs opacity-80">
            {eventsReceived} evento(s) recebido(s){lastEventAt ? ` · último ${formatRelative(lastEventAt)}` : ""}
          </p>
        )}
        {status === "reconnecting" && <p className="mt-1 text-xs opacity-80">Tentativa {attempt}</p>}
      </TooltipContent>
    </Tooltip>
  );
}
