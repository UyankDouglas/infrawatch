import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BellRing,
  CircleCheck,
  CircleHelp,
  CircleX,
  Gauge,
  History,
  Server,
  Siren,
  Timer,
  Wrench,
} from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { PeriodSelect } from "@/components/PeriodSelect";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { KpiCard } from "@/features/dashboard/KpiCard";
import { AvailabilityChart, LatencyChart, StatusDonut, TypeBars } from "@/features/dashboard/charts";
import { usePeriod } from "@/hooks/usePeriod";
import { dashboardApi } from "@/services/endpoints";
import { formatDuration, formatMs, formatPercent, formatRelative } from "@/lib/format";

const REFRESH_MS = 15_000;

export function OverviewPage() {
  const [period, setPeriod] = usePeriod("24h");
  const overview = useQuery({
    queryKey: ["dashboard", "overview", period],
    queryFn: () => dashboardApi.overview(period),
    refetchInterval: REFRESH_MS,
  });
  const availability = useQuery({
    queryKey: ["dashboard", "availability", period],
    queryFn: () => dashboardApi.availability(period),
    refetchInterval: REFRESH_MS,
  });
  const latency = useQuery({
    queryKey: ["dashboard", "latency", period],
    queryFn: () => dashboardApi.latency(period, 5),
    refetchInterval: REFRESH_MS,
  });

  const o = overview.data;
  const loading = overview.isLoading;

  return (
    <>
      <PageHeader
        title="Visão Geral"
        description="Indicadores calculados a partir das verificações persistidas. Atualização automática a cada 15 segundos."
        actions={<PeriodSelect value={period} onChange={setPeriod} />}
      />

      {overview.isError && (
        <Alert variant="destructive">
          <AlertTitle>Não foi possível carregar os indicadores</AlertTitle>
          <AlertDescription>Verifique se a API está em execução e tente novamente.</AlertDescription>
        </Alert>
      )}

      {!loading && o && o.totalResources === 0 ? (
        <EmptyState
          icon={Server}
          title="Nenhum recurso monitorado ainda"
          description="Cadastre um serviço HTTP autorizado para que o InfraWatch comece a verificar sua disponibilidade automaticamente."
          action={
            <Button asChild>
              <Link to="/recursos?novo=1">Cadastrar primeiro recurso</Link>
            </Button>
          }
        />
      ) : (
        <>
          <section aria-label="Indicadores" className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
            <KpiCard
              title="Recursos monitorados"
              value={o?.totalResources}
              icon={Server}
              loading={loading}
              hint={o && o.pausedResources > 0 ? `${o.pausedResources} pausado(s)` : undefined}
            />
            <KpiCard title="Disponíveis" value={o?.available} icon={CircleCheck} tone="up" loading={loading} />
            <KpiCard title="Indisponíveis" value={o?.unavailable} icon={CircleX} tone="down" loading={loading} />
            <KpiCard title="Degradados" value={o?.degraded} icon={AlertTriangle} tone="degraded" loading={loading} />
            <KpiCard
              title="Sem dados suficientes"
              value={o?.withoutData}
              icon={CircleHelp}
              tone="muted"
              loading={loading}
              hint={o ? `${o.pendingResources} aguardando · ${o.unknownResources} sem coleta` : undefined}
            />
            <KpiCard
              title="Incidentes ativos"
              value={o ? o.activeIncidents : undefined}
              icon={Siren}
              tone="info"
              loading={loading}
              hint="Módulo da Fase 4"
            />
            <KpiCard
              title="Alertas ativos"
              value={o ? o.activeAlerts : undefined}
              icon={BellRing}
              tone="info"
              loading={loading}
              hint="Módulo da Fase 3"
            />
            <KpiCard
              title="Disponibilidade no período"
              value={o?.availabilityPercent == null ? null : formatPercent(o.availabilityPercent)}
              icon={Gauge}
              tone="up"
              loading={loading}
              hint={o && o.observedSeconds > 0 ? `${formatDuration(o.observedSeconds)} observados` : undefined}
            />
            <KpiCard
              title="Tempo médio de resposta"
              value={o?.averageResponseMs == null ? null : formatMs(o.averageResponseMs)}
              icon={Timer}
              loading={loading}
              hint={o ? `${o.checksInPeriod} verificações · ${o.failedChecksInPeriod} falhas` : undefined}
            />
            <KpiCard
              title="Próximas manutenções"
              value={o ? 0 : undefined}
              icon={Wrench}
              tone="muted"
              loading={loading}
              hint="Módulo da Fase 4"
            />
          </section>

          <section className="grid gap-4 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Disponibilidade por período</CardTitle>
                <CardDescription>Percentual de verificações bem-sucedidas em cada intervalo.</CardDescription>
              </CardHeader>
              <CardContent>
                {availability.isLoading ? (
                  <Skeleton className="h-[240px] w-full" />
                ) : availability.data && availability.data.points.length > 0 ? (
                  <AvailabilityChart data={availability.data} />
                ) : (
                  <EmptyState
                    compact
                    icon={Activity}
                    title="Sem verificações no período"
                    description="O gráfico aparece assim que houver verificações registradas."
                  />
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Distribuição de status</CardTitle>
                <CardDescription>Estado atual dos recursos.</CardDescription>
              </CardHeader>
              <CardContent>
                {loading ? (
                  <Skeleton className="h-[150px] w-full" />
                ) : o ? (
                  <StatusDonut data={o.statusDistribution} />
                ) : null}
              </CardContent>
            </Card>
          </section>

          <section className="grid gap-4 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Latência por serviço</CardTitle>
                <CardDescription>Tempo médio de resposta dos serviços com maior latência no período.</CardDescription>
              </CardHeader>
              <CardContent>
                {latency.isLoading ? (
                  <Skeleton className="h-[260px] w-full" />
                ) : latency.data && latency.data.series.length > 0 ? (
                  <LatencyChart data={latency.data} />
                ) : (
                  <EmptyState
                    compact
                    icon={Timer}
                    title="Sem medições de latência"
                    description="Aguardando verificações bem-sucedidas no período."
                  />
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Recursos por tipo</CardTitle>
                <CardDescription>Quantidade cadastrada por categoria.</CardDescription>
              </CardHeader>
              <CardContent>
                {loading ? <Skeleton className="h-[150px] w-full" /> : o ? <TypeBars data={o.resourcesByType} /> : null}
              </CardContent>
            </Card>
          </section>

          <section className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader className="flex-row items-start justify-between">
                <div>
                  <CardTitle>Recursos com problemas</CardTitle>
                  <CardDescription>Indisponíveis e degradados neste momento.</CardDescription>
                </div>
                <Button asChild variant="ghost" size="sm">
                  <Link to="/recursos?status=DOWN">
                    Ver todos <ArrowRight className="size-4" />
                  </Link>
                </Button>
              </CardHeader>
              <CardContent>
                {loading ? (
                  <div className="space-y-2">
                    {[0, 1, 2].map((i) => (
                      <Skeleton key={i} className="h-12 w-full" />
                    ))}
                  </div>
                ) : o && o.problematic.length > 0 ? (
                  <ul className="divide-y divide-border">
                    {o.problematic.map((r) => (
                      <li key={r.id}>
                        <Link
                          to={`/recursos/${r.id}`}
                          className="flex items-center gap-3 py-2.5 hover:bg-accent/40 focus-visible:bg-accent/40 outline-none rounded-md px-1 -mx-1"
                        >
                          <StatusBadge status={r.status} size="sm" pulse />
                          <div className="min-w-0 flex-1">
                            <p className="text-sm font-medium">{r.name}</p>
                            <p className="text-xs text-muted-foreground">
                              {r.environmentLabel}
                              {r.lastErrorCode ? ` · ${r.lastErrorCode}` : ""}
                              {r.lastResponseTimeMs != null ? ` · ${formatMs(r.lastResponseTimeMs)}` : ""}
                            </p>
                          </div>
                          <span className="text-xs text-muted-foreground whitespace-nowrap">
                            desde {formatRelative(r.statusSince)}
                          </span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <EmptyState
                    compact
                    icon={CircleCheck}
                    title="Nenhum recurso com problema"
                    description="Todos os recursos verificados estão respondendo dentro do esperado."
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Últimas transições de estado</CardTitle>
                <CardDescription>Registro real das mudanças detectadas pelo motor de verificações.</CardDescription>
              </CardHeader>
              <CardContent>
                {loading ? (
                  <div className="space-y-2">
                    {[0, 1, 2].map((i) => (
                      <Skeleton key={i} className="h-12 w-full" />
                    ))}
                  </div>
                ) : o && o.recentTransitions.length > 0 ? (
                  <ol className="relative space-y-0 border-l border-border pl-4">
                    {o.recentTransitions.slice(0, 8).map((t) => (
                      <li key={t.id} className="relative py-2">
                        <span
                          className="absolute top-3.5 -left-[21px] status-dot"
                          style={{ background: `var(--status-${t.toStatus.toLowerCase()})` }}
                          aria-hidden
                        />
                        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                          <Link to={`/recursos/${t.resourceId}`} className="font-medium hover:underline">
                            {t.resourceName}
                          </Link>
                          <span className="text-muted-foreground">{t.fromLabel}</span>
                          <ArrowRight className="size-3 text-muted-foreground" aria-hidden />
                          <StatusBadge status={t.toStatus} size="sm" />
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {formatRelative(t.changedAt)}
                          {t.reason ? ` · ${t.reason}` : ""}
                        </p>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <EmptyState
                    compact
                    icon={History}
                    title="Nenhuma transição registrada"
                    description="As mudanças de estado aparecerão aqui conforme forem detectadas."
                  />
                )}
              </CardContent>
            </Card>
          </section>

          <Card>
            <CardHeader className="flex-row items-start justify-between">
              <div>
                <CardTitle>Serviços monitorados</CardTitle>
                <CardDescription>Estado atual e última verificação de cada recurso.</CardDescription>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link to="/recursos">
                  Gerenciar <ArrowRight className="size-4" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent>
              {loading ? (
                <Skeleton className="h-32 w-full" />
              ) : (
                <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                  {o?.resources.map((r) => (
                    <Link
                      key={r.id}
                      to={`/recursos/${r.id}`}
                      className="flex items-center gap-3 rounded-lg border border-border bg-card/60 p-3 transition-colors hover:border-primary/50 hover:bg-accent/40 outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <StatusBadge status={r.status} size="sm" pulse />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{r.name}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {r.typeLabel} · {r.environmentLabel}
                        </p>
                      </div>
                      <div className="text-right text-xs text-muted-foreground">
                        <p className="font-medium tabular-nums text-foreground">{formatMs(r.lastResponseTimeMs)}</p>
                        <p>{r.lastCheckAt ? formatRelative(r.lastCheckAt) : "não verificado"}</p>
                      </div>
                    </Link>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {o && (
            <p className="text-xs text-muted-foreground">
              <strong className="font-medium text-foreground">Como a disponibilidade é calculada:</strong>{" "}
              {o.availabilityNote}
            </p>
          )}
        </>
      )}
    </>
  );
}
