import { useState } from "react";
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
import { AlertStatusBadge, SeverityBadge } from "@/features/alerts/SeverityBadge";
import { IncidentStatusBadge, MaintenanceStatusBadge } from "@/features/incidents/IncidentBadges";
import { IncidentsChart } from "@/features/dashboard/charts";
import { incidentsApi } from "@/services/endpoints";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AvailabilityChart, LatencyChart, StatusDonut, TypeBars } from "@/features/dashboard/charts";
import { usePeriod } from "@/hooks/usePeriod";
import { dashboardApi } from "@/services/endpoints";
import { formatDateTime, formatDuration, formatMs, formatPercent, formatRelative } from "@/lib/format";

const REFRESH_MS = 15_000;

export function OverviewPage() {
  const [period, setPeriod] = usePeriod("24h");
  const [maintenanceMode, setMaintenanceMode] = useState<"excluded" | "counted">("excluded");
  const overview = useQuery({
    queryKey: ["dashboard", "overview", period, maintenanceMode],
    queryFn: () => dashboardApi.overview(period, maintenanceMode),
    refetchInterval: REFRESH_MS,
  });
  const incidentSeries = useQuery({
    queryKey: ["dashboard", "incidents", period],
    queryFn: () => incidentsApi.series(period),
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
        actions={
          <>
            <Select value={maintenanceMode} onValueChange={(v) => setMaintenanceMode(v as "excluded" | "counted")}>
              <SelectTrigger className="w-[230px]" aria-label="Tratamento de manutenções">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="excluded">Manutenções excluídas</SelectItem>
                <SelectItem value="counted">Manutenções como indisponíveis</SelectItem>
              </SelectContent>
            </Select>
            <PeriodSelect value={period} onChange={setPeriod} />
          </>
        }
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
              value={o?.activeIncidents}
              icon={Siren}
              tone={o && o.activeIncidents > 0 ? "down" : "info"}
              loading={loading}
              hint="Abertos, reconhecidos e em investigação"
            />
            <KpiCard
              title="Alertas ativos"
              value={o?.activeAlerts}
              icon={BellRing}
              tone={o && o.activeAlerts > 0 ? "down" : "info"}
              loading={loading}
              hint="Abertos e reconhecidos"
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
              value={o ? o.upcomingMaintenances.length : undefined}
              icon={Wrench}
              tone="info"
              loading={loading}
              hint={
                o?.upcomingMaintenances[0]
                  ? `${o.upcomingMaintenances[0].title} · ${formatRelative(o.upcomingMaintenances[0].startsAt)}`
                  : "Nenhuma agendada"
              }
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

          <section className="grid gap-4 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Incidentes ao longo do tempo</CardTitle>
                <CardDescription>Incidentes abertos por intervalo (automáticos e manuais).</CardDescription>
              </CardHeader>
              <CardContent>
                {incidentSeries.isLoading ? (
                  <Skeleton className="h-[200px] w-full" />
                ) : incidentSeries.data && incidentSeries.data.points.length > 0 ? (
                  <IncidentsChart data={incidentSeries.data} />
                ) : (
                  <EmptyState
                    compact
                    icon={Siren}
                    title="Nenhum incidente no período"
                    description="Ótimo sinal: nada foi aberto neste intervalo."
                  />
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex-row items-start justify-between">
                <div>
                  <CardTitle>Próximas manutenções</CardTitle>
                  <CardDescription>Agendadas e em andamento.</CardDescription>
                </div>
                <Button asChild variant="ghost" size="sm">
                  <Link to="/manutencoes">
                    Ver <ArrowRight className="size-4" />
                  </Link>
                </Button>
              </CardHeader>
              <CardContent>
                {loading ? (
                  <Skeleton className="h-24 w-full" />
                ) : o && o.upcomingMaintenances.length > 0 ? (
                  <ul className="divide-y divide-border">
                    {o.upcomingMaintenances.map((w) => (
                      <li key={w.id} className="py-2">
                        <div className="flex items-center gap-2">
                          <MaintenanceStatusBadge status={w.status} />
                          <span className="min-w-0 flex-1 truncate text-sm font-medium">{w.title}</span>
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {formatDateTime(w.startsAt, "dd/MM HH:mm")} → {formatDateTime(w.endsAt, "dd/MM HH:mm")} ·{" "}
                          {w.resources.length} recurso(s)
                        </p>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <EmptyState compact icon={Wrench} title="Nenhuma manutenção programada" />
                )}
              </CardContent>
            </Card>
          </section>

          <Card>
            <CardHeader className="flex-row items-start justify-between">
              <div>
                <CardTitle>Últimos incidentes</CardTitle>
                <CardDescription>Abertos automaticamente ou pela equipe.</CardDescription>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link to="/incidentes">
                  Ver incidentes <ArrowRight className="size-4" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent>
              {loading ? (
                <Skeleton className="h-24 w-full" />
              ) : o && o.recentIncidents.length > 0 ? (
                <ul className="divide-y divide-border">
                  {o.recentIncidents.slice(0, 6).map((i) => (
                    <li key={i.id}>
                      <Link
                        to={`/incidentes/${i.id}`}
                        className="-mx-1 flex flex-wrap items-center gap-2 rounded-md px-1 py-2 outline-none hover:bg-accent/40 focus-visible:bg-accent/40"
                      >
                        <span className="font-mono text-xs text-muted-foreground">#{i.number}</span>
                        <SeverityBadge severity={i.severity} />
                        <IncidentStatusBadge status={i.status} />
                        <span className="min-w-0 flex-1 truncate text-sm font-medium">{i.title}</span>
                        <span className="text-xs whitespace-nowrap text-muted-foreground">
                          {formatRelative(i.openedAt)}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState
                  compact
                  icon={Siren}
                  title="Nenhum incidente registrado"
                  description="Incidentes aparecem aqui quando um recurso fica indisponível ou quando a equipe abre um."
                />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex-row items-start justify-between">
              <div>
                <CardTitle>Alertas recentes</CardTitle>
                <CardDescription>Últimos alertas disparados nesta organização.</CardDescription>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link to="/alertas">
                  Ver alertas <ArrowRight className="size-4" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent>
              {loading ? (
                <Skeleton className="h-24 w-full" />
              ) : o && o.recentAlerts.length > 0 ? (
                <ul className="divide-y divide-border">
                  {o.recentAlerts.slice(0, 6).map((a) => (
                    <li key={a.id}>
                      <Link
                        to={`/alertas?alerta=${a.id}`}
                        className="-mx-1 flex flex-wrap items-center gap-2 rounded-md px-1 py-2 outline-none hover:bg-accent/40 focus-visible:bg-accent/40"
                      >
                        <SeverityBadge severity={a.severity} />
                        <AlertStatusBadge status={a.status} />
                        <span className="min-w-0 flex-1 truncate text-sm font-medium">{a.title}</span>
                        <span className="text-xs whitespace-nowrap text-muted-foreground">
                          {formatRelative(a.triggeredAt)}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState
                  compact
                  icon={BellRing}
                  title="Nenhum alerta registrado"
                  description="Alertas aparecem aqui quando uma regra for acionada."
                />
              )}
            </CardContent>
          </Card>

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
