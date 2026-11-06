import { useQuery } from "@tanstack/react-query";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Cpu, HardDrive, MemoryStick, Clock3, Gauge, ServerOff } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/EmptyState";
import { PeriodSelect } from "@/components/PeriodSelect";
import { agentsApi } from "@/services/endpoints";
import { bucketLabel, formatBytes, formatDateTime, formatPercent, formatRelative, formatUptime } from "@/lib/format";
import type { Period } from "@/types/api";
import type { AgentMetrics, MetricValue } from "@/types/metrics";
import { cn } from "@/lib/utils";

const GRID = "color-mix(in oklab, var(--border) 80%, transparent)";
const AXIS = "var(--muted-foreground)";
const tooltipStyle = {
  contentStyle: {
    background: "var(--popover)",
    border: "1px solid var(--border)",
    borderRadius: 8,
    color: "var(--popover-foreground)",
    fontSize: 12,
  },
  labelStyle: { color: "var(--muted-foreground)" },
};

function toneFor(value: number | null): string {
  if (value === null) return "text-muted-foreground";
  if (value >= 90) return "text-status-down";
  if (value >= 75) return "text-status-degraded";
  return "text-status-up";
}

function barColor(value: number | null): string {
  if (value === null) return "var(--muted-foreground)";
  if (value >= 90) return "var(--status-down)";
  if (value >= 75) return "var(--status-degraded)";
  return "var(--status-up)";
}

function GaugeCard({ metric, icon: Icon, detail }: { metric: MetricValue; icon: typeof Cpu; detail?: string }) {
  const percent = metric.unit === "%" ? metric.value : null;
  return (
    <div className="rounded-lg border border-border bg-card/60 p-4" data-testid={`metric-${metric.key}`}>
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-2 text-sm text-muted-foreground">
          <Icon className="size-4" aria-hidden /> {metric.label}
        </span>
        {!metric.available && (
          <Badge variant="outline" className="text-[10px]">
            Indisponível
          </Badge>
        )}
      </div>
      {metric.available ? (
        <>
          <p className={cn("mt-2 text-2xl font-semibold tabular-nums", toneFor(percent))}>
            {metric.unit === "%"
              ? formatPercent(metric.value, 1)
              : metric.key === "uptime"
                ? formatUptime(metric.value)
                : (metric.value ?? "—")}
          </p>
          {percent !== null && (
            <div
              className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-muted"
              role="progressbar"
              aria-valuenow={percent}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label={metric.label}
            >
              <div
                className="h-full rounded-full"
                style={{ width: `${Math.min(100, percent)}%`, background: barColor(percent) }}
              />
            </div>
          )}
          {detail && <p className="mt-1 text-xs text-muted-foreground">{detail}</p>}
        </>
      ) : (
        <p className="mt-2 text-sm text-muted-foreground">{metric.note ?? "Métrica não reportada pelo agente"}</p>
      )}
    </div>
  );
}

function MetricChart({
  data,
  bucketSeconds,
  avgKey,
  maxKey,
  label,
  color,
}: {
  data: AgentMetrics["series"];
  bucketSeconds: number;
  avgKey: "cpuAvg" | "memPercentAvg" | "diskPercentAvg";
  maxKey: "cpuMax" | "memPercentMax" | "diskPercentMax";
  label: string;
  color: string;
}) {
  const rows = data.map((p) => ({ ...p, label: bucketLabel(p.bucket, bucketSeconds) }));
  const id = `fill-${avgKey}`;
  return (
    <div>
      <p className="mb-1 text-xs font-medium text-muted-foreground">{label}</p>
      <ResponsiveContainer width="100%" height={160}>
        <AreaChart data={rows} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
          <defs>
            <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity={0.35} />
              <stop offset="100%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis
            dataKey="label"
            stroke={AXIS}
            tick={{ fontSize: 10 }}
            tickLine={false}
            axisLine={false}
            minTickGap={28}
          />
          <YAxis domain={[0, 100]} stroke={AXIS} tick={{ fontSize: 10 }} tickLine={false} axisLine={false} unit="%" />
          <Tooltip
            {...tooltipStyle}
            formatter={(value, name) => [
              formatPercent(typeof value === "number" ? value : null, 1),
              name === maxKey ? "Pico" : "Média",
            ]}
            labelFormatter={(_, payload) =>
              formatDateTime(payload?.[0]?.payload?.bucket as string | undefined, "dd/MM HH:mm")
            }
          />
          <Area
            type="monotone"
            dataKey={maxKey}
            stroke={color}
            strokeWidth={1}
            strokeDasharray="4 3"
            fill="none"
            connectNulls
            isAnimationActive={false}
          />
          <Area
            type="monotone"
            dataKey={avgKey}
            stroke={color}
            strokeWidth={2}
            fill={`url(#${id})`}
            connectNulls
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

const GRANULARITY_LABEL: Record<AgentMetrics["granularity"], string> = {
  none: "",
  raw: "amostras brutas",
  raw_fallback: "amostras brutas (agregações ainda não geradas)",
  rollup_5m: "agregado a cada 5 min",
  rollup_1h: "agregado a cada 1 h",
};

export function AgentMetricsPanel({
  resourceId,
  period,
  onPeriodChange,
}: {
  resourceId: string;
  period: Period;
  onPeriodChange: (p: Period) => void;
}) {
  const query = useQuery({
    queryKey: ["resources", "agent-metrics", resourceId, period],
    queryFn: () => agentsApi.metrics(resourceId, period),
    refetchInterval: 15_000,
  });

  const m = query.data;
  const byKey = (key: string): MetricValue =>
    m?.values.find((v) => v.key === key) ?? { key, label: key, value: null, unit: "", available: false, note: null };

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-3">
        <div>
          <CardTitle className="flex items-center gap-2">
            Métricas do servidor
            <Badge variant="secondary" className="font-normal">
              Fonte: agente autorizado
            </Badge>
            {m?.stale && <Badge className="bg-status-unknown/15 text-status-unknown">Agente silencioso</Badge>}
          </CardTitle>
          <CardDescription>
            CPU, memória, disco e uptime coletados no próprio servidor. Nada aqui é inferido de verificações HTTP.
            {m?.latest ? ` Última amostra ${formatRelative(m.latest.sampledAt)}.` : ""}
          </CardDescription>
        </div>
        <PeriodSelect value={period} onChange={onPeriodChange} />
      </CardHeader>
      <CardContent className="space-y-5">
        {query.isLoading ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-24 w-full" />
            ))}
          </div>
        ) : !m || !m.hasAgent ? (
          <EmptyState
            compact
            icon={ServerOff}
            title="Nenhum agente registrado"
            description="Métricas de CPU, memória e disco exigem um agente autorizado instalado no servidor. Registre um agente na aba Agente."
          />
        ) : !m.latest ? (
          <EmptyState
            compact
            icon={Gauge}
            title="Aguardando a primeira amostra"
            description={`O agente "${m.agent?.name}" está registrado, mas ainda não enviou dados. Verifique se ele está em execução com o token correto.`}
          />
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <GaugeCard
                metric={byKey("cpu")}
                icon={Cpu}
                detail={m.latest.loadAvg1m != null ? `Carga 1 min: ${m.latest.loadAvg1m}` : undefined}
              />
              <GaugeCard
                metric={byKey("memory")}
                icon={MemoryStick}
                detail={
                  m.latest.memTotalBytes
                    ? `${formatBytes(m.latest.memUsedBytes)} de ${formatBytes(m.latest.memTotalBytes)}`
                    : undefined
                }
              />
              <GaugeCard
                metric={byKey("disk")}
                icon={HardDrive}
                detail={
                  m.latest.diskTotalBytes
                    ? `${formatBytes(m.latest.diskUsedBytes)} de ${formatBytes(m.latest.diskTotalBytes)}${m.latest.diskPath ? ` em ${m.latest.diskPath}` : ""}`
                    : undefined
                }
              />
              <GaugeCard
                metric={byKey("uptime")}
                icon={Clock3}
                detail={`Coletado ${formatDateTime(m.latest.sampledAt)}`}
              />
            </div>

            {m.series.length > 0 ? (
              <div className="grid gap-4 lg:grid-cols-3">
                <MetricChart
                  data={m.series}
                  bucketSeconds={m.bucketSeconds}
                  avgKey="cpuAvg"
                  maxKey="cpuMax"
                  label="CPU (%)"
                  color="#3b82f6"
                />
                <MetricChart
                  data={m.series}
                  bucketSeconds={m.bucketSeconds}
                  avgKey="memPercentAvg"
                  maxKey="memPercentMax"
                  label="Memória (%)"
                  color="#8b5cf6"
                />
                <MetricChart
                  data={m.series}
                  bucketSeconds={m.bucketSeconds}
                  avgKey="diskPercentAvg"
                  maxKey="diskPercentMax"
                  label="Disco (%)"
                  color="#06b6d4"
                />
              </div>
            ) : (
              <EmptyState
                compact
                icon={Gauge}
                title="Sem série no período"
                description="Ajuste o período ou aguarde novas amostras."
              />
            )}

            <p className="text-xs text-muted-foreground">
              {m.note}
              {m.granularity !== "none" ? ` Série: ${GRANULARITY_LABEL[m.granularity]}.` : ""}
              {m.missingMetrics.length > 0 ? ` Não reportadas: ${m.missingMetrics.join(", ")}.` : ""}
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}
