import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  ArrowLeft,
  ArrowRight,
  CircleCheck,
  CircleX,
  Clock,
  Gauge,
  History,
  Pause,
  Pencil,
  Play,
  RefreshCw,
  Timer,
  Trash2,
} from "lucide-react";
import { toast } from "sonner";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { PeriodSelect } from "@/components/PeriodSelect";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { KpiCard } from "@/features/dashboard/KpiCard";
import { ResourceLatencyChart } from "@/features/dashboard/charts";
import { ResourceFormDialog } from "@/features/resources/ResourceFormDialog";
import { AgentMetricsPanel } from "@/features/metrics/AgentMetricsPanel";
import { AgentPanel } from "@/features/metrics/AgentPanel";
import { ResourceAlerts } from "@/features/alerts/ResourceAlerts";
import { useAuth } from "@/features/auth/AuthProvider";
import { usePeriod } from "@/hooks/usePeriod";
import { ApiError } from "@/services/api";
import { resourcesApi } from "@/services/endpoints";
import {
  errorLabel,
  formatDateTime,
  formatDuration,
  formatInterval,
  formatMs,
  formatPercent,
  formatRelative,
} from "@/lib/format";

export function ResourceDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const [period, setPeriod] = usePeriod("6h");
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const resource = useQuery({
    queryKey: ["resources", "detail", id],
    queryFn: () => resourcesApi.get(id),
    refetchInterval: 10_000,
  });
  const checks = useQuery({
    queryKey: ["resources", "checks", id],
    queryFn: () => resourcesApi.checks(id, 0, 30),
    refetchInterval: 10_000,
  });
  const history = useQuery({
    queryKey: ["resources", "history", id],
    queryFn: () => resourcesApi.history(id, 0, 30),
    refetchInterval: 15_000,
  });
  const metrics = useQuery({
    queryKey: ["resources", "metrics", id, period],
    queryFn: () => resourcesApi.metrics(id, period),
    refetchInterval: 15_000,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["resources"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };

  const runNow = useMutation({
    mutationFn: () => resourcesApi.runNow(id),
    onSuccess: (r) => {
      invalidate();
      if (!r.executed) toast.warning(r.message);
      else if (r.transitioned) toast.info(r.message);
      else
        toast.success(
          `Verificação concluída: ${r.execution?.success ? "sucesso" : "falha"} em ${formatMs(r.execution?.responseTimeMs)}.`,
        );
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao executar a verificação."),
  });

  const toggleEnabled = useMutation({
    mutationFn: (enabled: boolean) => resourcesApi.update(id, { enabled }),
    onSuccess: (r) => {
      invalidate();
      toast.success(r.enabled ? "Monitoramento retomado." : "Monitoramento pausado.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao atualizar."),
  });

  const remove = useMutation({
    mutationFn: () => resourcesApi.remove(id),
    onSuccess: () => {
      invalidate();
      toast.success("Recurso removido.");
      navigate("/recursos");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao remover."),
  });

  if (resource.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }
  if (resource.isError || !resource.data) {
    return (
      <EmptyState
        icon={CircleX}
        title="Recurso não encontrado"
        description="Ele pode ter sido removido ou pertencer a outra organização."
        action={
          <Button asChild variant="outline">
            <Link to="/recursos">Voltar para a lista</Link>
          </Button>
        }
      />
    );
  }

  const r = resource.data;
  const m = metrics.data;
  const lastChecks = checks.data?.items ?? [];
  const okCount = lastChecks.filter((c) => c.success).length;

  return (
    <>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-2">
          <Link
            to="/recursos"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="size-4" aria-hidden /> Recursos Monitorados
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">{r.name}</h1>
            <StatusBadge status={r.status} pulse />
            {!r.enabled && <Badge variant="outline">Pausado</Badge>}
          </div>
          <p className="truncate font-mono text-sm text-muted-foreground">{r.targetUrl}</p>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span>{r.typeLabel}</span>
            <span>{r.environmentLabel}</span>
            {r.group && <span>Grupo: {r.group.name}</span>}
            {r.owner && <span>Responsável: {r.owner}</span>}
            <span>Cadastrado em {formatDateTime(r.createdAt, "dd/MM/yyyy")}</span>
          </div>
          {r.tags.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {r.tags.map((t) => (
                <Badge key={t} variant="secondary">
                  {t}
                </Badge>
              ))}
            </div>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {hasRole("OPERATOR") && r.type !== "AGENT_SERVER" && (
            <Button variant="outline" onClick={() => runNow.mutate()} disabled={runNow.isPending}>
              <RefreshCw className={runNow.isPending ? "size-4 animate-spin" : "size-4"} aria-hidden /> Verificar agora
            </Button>
          )}
          {hasRole("ADMIN") && (
            <>
              <Button
                variant="outline"
                onClick={() => toggleEnabled.mutate(!r.enabled)}
                disabled={toggleEnabled.isPending}
              >
                {r.enabled ? <Pause className="size-4" aria-hidden /> : <Play className="size-4" aria-hidden />}
                {r.enabled ? "Pausar" : "Retomar"}
              </Button>
              <Button variant="outline" onClick={() => setEditOpen(true)}>
                <Pencil className="size-4" aria-hidden /> Editar
              </Button>
              <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
                <Trash2 className="size-4" aria-hidden /> Remover
              </Button>
            </>
          )}
        </div>
      </div>

      <ResourceAlerts resourceId={r.id} />

      <section className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        {r.type !== "AGENT_SERVER" && (
          <KpiCard
            title="Última resposta"
            value={r.lastResponseTimeMs == null ? null : formatMs(r.lastResponseTimeMs)}
            icon={Timer}
            hint={r.lastHttpStatus ? `HTTP ${r.lastHttpStatus}` : undefined}
            emptyLabel="Sem verificação"
          />
        )}
        <KpiCard
          title="Última verificação"
          value={r.lastCheckAt ? formatRelative(r.lastCheckAt) : null}
          icon={Clock}
          tone="muted"
          hint={r.lastCheckAt ? formatDateTime(r.lastCheckAt) : undefined}
          emptyLabel="Ainda não verificado"
        />
        <KpiCard
          title="Neste estado há"
          value={formatRelative(r.statusSince).replace("há ", "")}
          icon={Activity}
          tone={
            r.status === "UP" ? "up" : r.status === "DOWN" ? "down" : r.status === "DEGRADED" ? "degraded" : "muted"
          }
          hint={`desde ${formatDateTime(r.statusSince, "dd/MM HH:mm")}`}
        />
        <KpiCard
          title="Disponibilidade"
          value={m?.availabilityPercent == null ? null : formatPercent(m.availabilityPercent)}
          icon={Gauge}
          tone="up"
          loading={metrics.isLoading}
          hint={m && m.observedSeconds > 0 ? `${formatDuration(m.observedSeconds)} observados` : undefined}
        />
        {r.type !== "AGENT_SERVER" && (
          <KpiCard
            title="Intervalo / timeout"
            value={r.check ? `${formatInterval(r.check.intervalSeconds)} / ${formatMs(r.check.timeoutMs)}` : null}
            icon={RefreshCw}
            tone="info"
            hint={
              r.check
                ? `Falhas p/ cair: ${r.check.failureThreshold} · p/ recuperar: ${r.check.recoveryThreshold}`
                : undefined
            }
          />
        )}
        {r.type !== "AGENT_SERVER" && (
          <KpiCard
            title="Últimas verificações"
            value={lastChecks.length ? `${okCount}/${lastChecks.length} OK` : null}
            icon={CircleCheck}
            tone={okCount === lastChecks.length ? "up" : "degraded"}
            loading={checks.isLoading}
            hint={r.lastErrorCode ? `Último erro: ${errorLabel(r.lastErrorCode)}` : undefined}
          />
        )}
      </section>

      {r.type === "AGENT_SERVER" && <AgentMetricsPanel resourceId={r.id} period={period} onPeriodChange={setPeriod} />}

      {r.type !== "AGENT_SERVER" && (
        <Card>
          <CardHeader className="flex-row items-start justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2">
                Tempo de resposta
                <Badge variant="secondary" className="font-normal">
                  Fonte: verificação HTTP
                </Badge>
              </CardTitle>
              <CardDescription>
                Média e pico por intervalo, com base nas verificações bem-sucedidas.
                {r.check?.degradedLatencyMs ? ` Limite de degradação: ${formatMs(r.check.degradedLatencyMs)}.` : ""}
              </CardDescription>
            </div>
            <PeriodSelect value={period} onChange={setPeriod} />
          </CardHeader>
          <CardContent>
            {metrics.isLoading ? (
              <Skeleton className="h-[260px] w-full" />
            ) : m && m.latency.length > 0 ? (
              <ResourceLatencyChart
                points={m.latency}
                bucketSeconds={m.bucketSeconds}
                degradedMs={r.check?.degradedLatencyMs}
              />
            ) : (
              <EmptyState
                compact
                icon={Timer}
                title="Sem medições no período"
                description="O gráfico aparece após as primeiras verificações."
              />
            )}
            {m && <p className="mt-3 text-xs text-muted-foreground">{m.availabilityNote}</p>}
          </CardContent>
        </Card>
      )}

      <Tabs defaultValue={r.type === "AGENT_SERVER" ? "agent" : "checks"}>
        <TabsList>
          {r.type === "AGENT_SERVER" && <TabsTrigger value="agent">Agente</TabsTrigger>}
          <TabsTrigger value="checks">Verificações</TabsTrigger>
          <TabsTrigger value="history">Histórico de estados</TabsTrigger>
          <TabsTrigger value="config">Configuração</TabsTrigger>
        </TabsList>

        {r.type === "AGENT_SERVER" && (
          <TabsContent value="agent">
            <AgentPanel resourceId={r.id} resourceName={r.name} />
          </TabsContent>
        )}

        <TabsContent value="checks">
          <Card className="gap-0 py-0">
            {checks.isLoading ? (
              <div className="space-y-2 p-4">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : lastChecks.length === 0 ? (
              <div className="p-4">
                <EmptyState
                  compact
                  icon={Activity}
                  title="Nenhuma verificação registrada"
                  description="A primeira verificação é executada em até alguns segundos após o cadastro."
                />
              </div>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Resultado</TableHead>
                      <TableHead>Data e hora</TableHead>
                      <TableHead>HTTP</TableHead>
                      <TableHead>Tempo de resposta</TableHead>
                      <TableHead>Erro</TableHead>
                      <TableHead>Endereço</TableHead>
                      <TableHead>Execução</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {lastChecks.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          {c.success ? (
                            <span className="inline-flex items-center gap-1.5 text-status-up">
                              <CircleCheck className="size-4" aria-hidden /> Sucesso
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 text-status-down">
                              <CircleX className="size-4" aria-hidden /> Falha
                            </span>
                          )}
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-sm">{formatDateTime(c.startedAt)}</TableCell>
                        <TableCell className="tabular-nums">{c.httpStatus ?? "—"}</TableCell>
                        <TableCell className="tabular-nums">{formatMs(c.responseTimeMs)}</TableCell>
                        <TableCell className="text-sm">
                          {c.errorCode ? (
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <span className="cursor-help underline decoration-dotted">
                                  {errorLabel(c.errorCode)}
                                </span>
                              </TooltipTrigger>
                              <TooltipContent className="max-w-xs">{c.errorMessage ?? c.errorCode}</TooltipContent>
                            </Tooltip>
                          ) : (
                            "—"
                          )}
                        </TableCell>
                        <TableCell className="font-mono text-xs text-muted-foreground">
                          {c.resolvedAddress ?? "—"}
                        </TableCell>
                        <TableCell className="font-mono text-xs text-muted-foreground">{c.runId.slice(0, 8)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </Card>
        </TabsContent>

        <TabsContent value="history">
          <Card>
            <CardContent>
              {history.isLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : (history.data?.items.length ?? 0) === 0 ? (
                <EmptyState
                  compact
                  icon={History}
                  title="Nenhuma transição registrada"
                  description="As mudanças de estado deste recurso aparecerão aqui."
                />
              ) : (
                <ol className="relative space-y-0 border-l border-border pl-5">
                  {history.data!.items.map((t) => (
                    <li key={t.id} className="relative py-3">
                      <span
                        className="absolute top-4 -left-[25px] status-dot ring-4 ring-card"
                        style={{ background: `var(--status-${t.toStatus.toLowerCase()})` }}
                        aria-hidden
                      />
                      <div className="flex flex-wrap items-center gap-2 text-sm">
                        <StatusBadge status={t.fromStatus} size="sm" />
                        <ArrowRight className="size-3.5 text-muted-foreground" aria-hidden />
                        <StatusBadge status={t.toStatus} size="sm" />
                        <span className="text-muted-foreground">{formatDateTime(t.changedAt)}</span>
                      </div>
                      {t.reason && <p className="mt-1 text-sm text-muted-foreground">{t.reason}</p>}
                    </li>
                  ))}
                </ol>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="config">
          <Card>
            <CardContent>
              {r.check ? (
                <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
                  {[
                    ["Intervalo de verificação", formatInterval(r.check.intervalSeconds)],
                    ["Timeout", formatMs(r.check.timeoutMs)],
                    ["Método HTTP", r.check.httpMethod],
                    ["Códigos esperados", r.check.expectedStatusCodes],
                    [
                      "Limite de degradação",
                      r.check.degradedLatencyMs ? formatMs(r.check.degradedLatencyMs) : "não definido",
                    ],
                    ["Seguir redirecionamentos", r.check.followRedirects ? "Sim (máx. 3, revalidados)" : "Não"],
                    ["Falhas consecutivas para Indisponível", String(r.check.failureThreshold)],
                    ["Sucessos consecutivos para recuperar", String(r.check.recoveryThreshold)],
                    ["Próxima verificação", r.enabled ? formatDateTime(r.nextCheckAt) : "pausado"],
                    ["Contadores atuais", `${r.consecutiveFailures} falha(s) · ${r.consecutiveSuccesses} sucesso(s)`],
                  ].map(([k, v]) => (
                    <div key={k}>
                      <dt className="text-muted-foreground">{k}</dt>
                      <dd className="font-medium">{v}</dd>
                    </div>
                  ))}
                </dl>
              ) : (
                <p className="text-sm text-muted-foreground">Este tipo de recurso não possui verificação HTTP.</p>
              )}
              {r.description && <p className="mt-4 text-sm text-muted-foreground">{r.description}</p>}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <ResourceFormDialog open={editOpen} onOpenChange={setEditOpen} resource={r} />
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Remover recurso"
        description={`"${r.name}" e todo o seu histórico de verificações serão removidos. Esta ação não pode ser desfeita.`}
        confirmLabel="Remover"
        destructive
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
    </>
  );
}
