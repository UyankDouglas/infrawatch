import { cn } from "@/lib/utils";
import type { IncidentStatus, MaintenanceStatus } from "@/types/incidents";

export const INCIDENT_STATUS_META: Record<IncidentStatus, { label: string; className: string }> = {
  OPEN: { label: "Aberto", className: "bg-status-down/12 text-status-down" },
  ACKNOWLEDGED: { label: "Reconhecido", className: "bg-primary/12 text-primary" },
  INVESTIGATING: { label: "Em investigação", className: "bg-status-degraded/12 text-status-degraded" },
  RESOLVED: { label: "Resolvido", className: "bg-status-up/12 text-status-up" },
  CLOSED: { label: "Encerrado", className: "bg-muted text-muted-foreground" },
};

export const MAINTENANCE_STATUS_META: Record<MaintenanceStatus, { label: string; className: string }> = {
  SCHEDULED: { label: "Agendada", className: "bg-primary/12 text-primary" },
  ACTIVE: { label: "Em andamento", className: "bg-status-maintenance/12 text-status-maintenance" },
  COMPLETED: { label: "Concluída", className: "bg-status-up/12 text-status-up" },
  CANCELLED: { label: "Cancelada", className: "bg-muted text-muted-foreground" },
};

export function IncidentStatusBadge({ status }: { status: IncidentStatus }) {
  const meta = INCIDENT_STATUS_META[status] ?? INCIDENT_STATUS_META.OPEN;
  return (
    <span
      className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium", meta.className)}
      data-status={status}
    >
      {meta.label}
    </span>
  );
}

export function MaintenanceStatusBadge({ status }: { status: MaintenanceStatus }) {
  const meta = MAINTENANCE_STATUS_META[status] ?? MAINTENANCE_STATUS_META.SCHEDULED;
  return (
    <span
      className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium", meta.className)}
      data-status={status}
    >
      {meta.label}
    </span>
  );
}
