import { useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { ChartColumnBig } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { LatencyTrendChart, MetricTrendChart } from "@/features/reports/ReportCharts";
import { reportsApi } from "@/services/endpoints";
import { formatDuration, formatMs, formatPercent } from "@/lib/format";
import type { Period } from "@/types/api";
import type { MaintenanceMode } from "@/types/reports";

function Stat({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="rounded-md border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-lg font-semibold tabular-nums">{value}</p>
      {note && <p className="text-xs text-muted-foreground">{note}</p>}
    </div>
  );
}

const sec = (v: number | null | undefined) => (v == null ? "—" : formatDuration(Math.round(v)));

export function ResourceReportPanel({
  resourceId,
  period,
  isAgent,
}: {
  resourceId: string;
  period: Period;
  isAgent: boolean;
}) {
  const [maintenance, setMaintenance] = useState<MaintenanceMode>("excluded");
  const report = useQuery({
    queryKey: ["reports", "resource", resourceId, period, maintenance],
    queryFn: () => reportsApi.resource(resourceId, period, maintenance),
  });

  if (report.isLoading) return <Skeleton className="h-64 w-full" />;
  const r = report.data;
  if (!r || !r.summary) return <EmptyState compact icon={ChartColumnBig} title="Sem dados para o relatório" />;
  const s = r.summary;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-muted-foreground">
          Disponibilidade = tempo disponível ÷ tempo observado (estado conhecido).{" "}
          <Link to="/relatorios" className="font-medium text-primary hover:underline">
            Ver relatório completo
          </Link>
        </p>
        <Select value={maintenance} onValueChange={(v) => setMaintenance(v as MaintenanceMode)}>
          <SelectTrigger className="w-[230px]" aria-label="Tratamento de manutenções">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="excluded">Manutenções excluídas</SelectItem>
            <SelectItem value="counted">Manutenções contam como indisponibilidade</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat
          label="Disponibilidade"
          value={s.availabilityPercent == null ? "sem dados" : formatPercent(s.availabilityPercent)}
          note={`observado ${sec(s.observedSeconds)}`}
        />
        <Stat
          label="Tempo indisponível"
          value={sec(s.unavailableSeconds)}
          note={`manutenção ${sec(s.maintenanceSeconds)}`}
        />
        <Stat
          label="Incidentes · MTTR"
          value={`${s.incidents} · ${sec(s.mttrSeconds)}`}
          note={s.mtbfSeconds != null ? `MTBF ${sec(s.mtbfSeconds)}` : (s.mtbfNote ?? undefined)}
        />
        <Stat
          label="Latência média · p95 · máx"
          value={`${formatMs(s.avgResponseMs)} · ${formatMs(s.p95ResponseMs)} · ${formatMs(s.maxResponseMs)}`}
          note={`${s.checks} verificações, ${s.failedChecks} falhas`}
        />
      </div>
      {r.latency.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Evolução da latência</CardTitle>
            <CardDescription>Média e máxima por intervalo, apenas verificações bem-sucedidas.</CardDescription>
          </CardHeader>
          <CardContent>
            <LatencyTrendChart points={r.latency} />
          </CardContent>
        </Card>
      )}
      {isAgent && (
        <Card>
          <CardHeader>
            <CardTitle>Evolução de CPU, memória e disco</CardTitle>
            <CardDescription>
              Granularidade:{" "}
              {r.metricGranularity === "raw" || r.metricGranularity === "raw_fallback"
                ? "amostras brutas"
                : r.metricGranularity === "rollup_5m"
                  ? "agregados de 5 min"
                  : "agregados de 1 h"}
              .
            </CardDescription>
          </CardHeader>
          <CardContent>
            {r.metricTrend.length === 0 ? (
              <EmptyState compact icon={ChartColumnBig} title="Sem amostras do agente no período" />
            ) : (
              <MetricTrendChart points={r.metricTrend} />
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
