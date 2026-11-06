import type { LucideIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

interface Props {
  title: string;
  value: string | number | null | undefined;
  hint?: string;
  icon: LucideIcon;
  tone?: "default" | "up" | "down" | "degraded" | "info" | "muted";
  loading?: boolean;
  emptyLabel?: string;
}

const TONES: Record<NonNullable<Props["tone"]>, string> = {
  default: "bg-primary/12 text-primary",
  up: "bg-status-up/12 text-status-up",
  down: "bg-status-down/12 text-status-down",
  degraded: "bg-status-degraded/12 text-status-degraded",
  info: "bg-status-maintenance/12 text-status-maintenance",
  muted: "bg-muted text-muted-foreground",
};

export function KpiCard({
  title,
  value,
  hint,
  icon: Icon,
  tone = "default",
  loading,
  emptyLabel = "Sem dados suficientes",
}: Props) {
  const isEmpty = value === null || value === undefined;
  return (
    <Card className="gap-0 py-0">
      <CardContent className="flex items-start gap-4 p-4">
        <div className={cn("flex size-10 shrink-0 items-center justify-center rounded-lg", TONES[tone])}>
          <Icon className="size-5" aria-hidden />
        </div>
        <div className="min-w-0 flex-1">
          <p className="line-clamp-2 text-xs font-medium leading-tight text-muted-foreground">{title}</p>
          {loading ? (
            <Skeleton className="mt-1.5 h-7 w-20" />
          ) : isEmpty ? (
            <p className="mt-1 text-sm font-medium text-muted-foreground">{emptyLabel}</p>
          ) : (
            <p className="mt-0.5 text-xl font-semibold tabular-nums tracking-tight break-words sm:text-2xl">{value}</p>
          )}
          {hint && !loading && <p className="mt-0.5 truncate text-xs text-muted-foreground">{hint}</p>}
        </div>
      </CardContent>
    </Card>
  );
}
