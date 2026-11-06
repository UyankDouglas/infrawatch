import { cn } from "@/lib/utils";
import type { AlertSeverity, AlertStatus } from "@/types/alerts";

export const SEVERITY_META: Record<AlertSeverity, { label: string; className: string; hex: string }> = {
  INFO: { label: "Informativo", className: "bg-status-maintenance/12 text-status-maintenance", hex: "#06b6d4" },
  WARNING: { label: "Atenção", className: "bg-status-degraded/12 text-status-degraded", hex: "#f59e0b" },
  HIGH: { label: "Alto", className: "bg-orange-500/12 text-orange-500", hex: "#f97316" },
  CRITICAL: { label: "Crítico", className: "bg-status-down/12 text-status-down", hex: "#ef4444" },
};

export const ALERT_STATUS_META: Record<AlertStatus, { label: string; className: string }> = {
  OPEN: { label: "Aberto", className: "bg-status-down/12 text-status-down" },
  ACKNOWLEDGED: { label: "Reconhecido", className: "bg-primary/12 text-primary" },
  RESOLVED: { label: "Resolvido", className: "bg-status-up/12 text-status-up" },
};

export function SeverityBadge({
  severity,
  label,
  className,
}: {
  severity: AlertSeverity;
  label?: string;
  className?: string;
}) {
  const meta = SEVERITY_META[severity] ?? SEVERITY_META.INFO;
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide",
        meta.className,
        className,
      )}
      data-severity={severity}
    >
      {label ?? meta.label}
    </span>
  );
}

export function AlertStatusBadge({ status, label }: { status: AlertStatus; label?: string }) {
  const meta = ALERT_STATUS_META[status] ?? ALERT_STATUS_META.OPEN;
  return (
    <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium", meta.className)}>
      {label ?? meta.label}
    </span>
  );
}
