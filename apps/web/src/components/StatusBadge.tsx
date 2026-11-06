import { cn } from "@/lib/utils";
import type { ResourceStatus } from "@/types/api";

export const STATUS_META: Record<ResourceStatus, { label: string; color: string; bg: string; text: string }> = {
  UP: { label: "Disponível", color: "bg-status-up", bg: "bg-status-up/12", text: "text-status-up" },
  DOWN: { label: "Indisponível", color: "bg-status-down", bg: "bg-status-down/12", text: "text-status-down" },
  DEGRADED: {
    label: "Degradado",
    color: "bg-status-degraded",
    bg: "bg-status-degraded/12",
    text: "text-status-degraded",
  },
  PENDING: {
    label: "Aguardando verificação",
    color: "bg-status-pending",
    bg: "bg-status-pending/12",
    text: "text-status-pending",
  },
  UNKNOWN: { label: "Sem dados", color: "bg-status-unknown", bg: "bg-status-unknown/12", text: "text-status-unknown" },
  MAINTENANCE: {
    label: "Em manutenção",
    color: "bg-status-maintenance",
    bg: "bg-status-maintenance/12",
    text: "text-status-maintenance",
  },
};

export const STATUS_HEX: Record<ResourceStatus, string> = {
  UP: "#10b981",
  DOWN: "#ef4444",
  DEGRADED: "#f59e0b",
  PENDING: "#94a3b8",
  UNKNOWN: "#a78bfa",
  MAINTENANCE: "#06b6d4",
};

interface Props {
  status: ResourceStatus;
  label?: string;
  size?: "sm" | "md";
  pulse?: boolean;
  className?: string;
}

export function StatusBadge({ status, label, size = "md", pulse, className }: Props) {
  const meta = STATUS_META[status] ?? STATUS_META.UNKNOWN;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full font-medium whitespace-nowrap",
        meta.bg,
        meta.text,
        size === "sm" ? "px-2 py-0.5 text-[11px]" : "px-2.5 py-1 text-xs",
        className,
      )}
      data-status={status}
    >
      <span
        className={cn(
          "status-dot",
          meta.color,
          pulse && (status === "DOWN" || status === "DEGRADED") && "status-dot-pulse",
        )}
        aria-hidden
      />
      {label ?? meta.label}
    </span>
  );
}

export function StatusDot({ status, className }: { status: ResourceStatus; className?: string }) {
  const meta = STATUS_META[status] ?? STATUS_META.UNKNOWN;
  return <span className={cn("status-dot", meta.color, className)} aria-label={meta.label} />;
}
