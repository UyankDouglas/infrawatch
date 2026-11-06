import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, Check, CheckCircle2, Pencil, Plus, ShieldAlert, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { KpiCard } from "@/features/dashboard/KpiCard";
import { AlertRuleDialog } from "@/features/alerts/AlertRuleDialog";
import { AlertStatusBadge, SeverityBadge } from "@/features/alerts/SeverityBadge";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { alertsApi } from "@/services/endpoints";
import { formatDateTime, formatDuration, formatRelative } from "@/lib/format";
import type { AlertEvent, AlertRule, AlertSeverity, AlertStatus } from "@/types/alerts";

const ALL = "all";

function AlertDetail({ alert, onClose }: { alert: AlertEvent | null; onClose: () => void }) {
  const { hasRole } = useAuth();
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["alerts"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };
  const ack = useMutation({
    mutationFn: () => alertsApi.acknowledge(alert!.id, note),
    onSuccess: () => {
      invalidate();
      setNote("");
      toast.success("Alerta reconhecido.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao reconhecer."),
  });
  const resolve = useMutation({
    mutationFn: () => alertsApi.resolve(alert!.id, note),
    onSuccess: () => {
      invalidate();
      setNote("");
      toast.success("Alerta resolvido manualmente.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao resolver."),
  });

  return (
    <Sheet open={!!alert} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
        {alert && (
          <>
            <SheetHeader>
              <div className="flex flex-wrap items-center gap-2">
                <SeverityBadge severity={alert.severity} />
                <AlertStatusBadge status={alert.status} />
              </div>
              <SheetTitle>{alert.title}</SheetTitle>
              <SheetDescription>{alert.message ?? alert.metricLabel}</SheetDescription>
            </SheetHeader>
            <div className="space-y-4 px-4 pb-6">
              <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
                <div>
                  <dt className="text-muted-foreground">Recurso</dt>
                  <dd className="font-medium">
                    {alert.resourceId ? (
                      <Link to={`/recursos/${alert.resourceId}`} className="hover:underline">
                        {alert.resourceName}
                      </Link>
                    ) : (
                      "—"
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Regra</dt>
                  <dd className="font-medium">{alert.ruleName ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Disparado em</dt>
                  <dd className="font-medium">{formatDateTime(alert.triggeredAt)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Duração</dt>
                  <dd className="font-medium">
                    {alert.durationSeconds != null ? formatDuration(alert.durationSeconds) : "—"}
                  </dd>
                </div>
                {alert.value != null && (
                  <div>
                    <dt className="text-muted-foreground">Valor / limite</dt>
                    <dd className="font-medium tabular-nums">
                      {alert.value} {alert.unit} / {alert.threshold ?? "—"} {alert.unit}
                    </dd>
                  </div>
                )}
                <div>
                  <dt className="text-muted-foreground">Ocorrências avaliadas</dt>
                  <dd className="font-medium">{alert.occurrenceCount}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Notificações enviadas</dt>
                  <dd className="font-medium">{alert.notificationCount}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Última avaliação</dt>
                  <dd className="font-medium">{formatRelative(alert.lastEvaluatedAt)}</dd>
                </div>
              </dl>

              <ol className="relative space-y-3 border-l border-border pl-4 text-sm">
                <li>
                  <span className="absolute -left-[5px] status-dot bg-status-down" aria-hidden />
                  <p className="font-medium">Disparado</p>
                  <p className="text-xs text-muted-foreground">{formatDateTime(alert.triggeredAt)}</p>
                </li>
                {alert.acknowledgedAt && (
                  <li>
                    <span className="absolute -left-[5px] status-dot bg-primary" aria-hidden />
                    <p className="font-medium">Reconhecido por {alert.acknowledgedByName ?? "—"}</p>
                    <p className="text-xs text-muted-foreground">{formatDateTime(alert.acknowledgedAt)}</p>
                    {alert.acknowledgeNote && <p className="text-xs">{alert.acknowledgeNote}</p>}
                  </li>
                )}
                {alert.resolvedAt && (
                  <li>
                    <span className="absolute -left-[5px] status-dot bg-status-up" aria-hidden />
                    <p className="font-medium">
                      Resolvido{" "}
                      {alert.resolution === "MANUAL"
                        ? `por ${alert.resolvedByName ?? "—"}`
                        : alert.resolution === "RULE_DISABLED"
                          ? "(regra desativada)"
                          : "(condição normalizada)"}
                    </p>
                    <p className="text-xs text-muted-foreground">{formatDateTime(alert.resolvedAt)}</p>
                    {alert.resolutionNote && <p className="text-xs">{alert.resolutionNote}</p>}
                  </li>
                )}
              </ol>

              {alert.status !== "RESOLVED" && hasRole("OPERATOR") && (
                <div className="space-y-2 rounded-md border border-border p-3">
                  <Textarea
                    placeholder="Observação (opcional)"
                    rows={2}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    maxLength={500}
                  />
                  <div className="flex flex-wrap gap-2">
                    {alert.status === "OPEN" && (
                      <Button size="sm" onClick={() => ack.mutate()} disabled={ack.isPending}>
                        <Check className="size-4" aria-hidden /> Reconhecer
                      </Button>
                    )}
                    <Button size="sm" variant="outline" onClick={() => resolve.mutate()} disabled={resolve.isPending}>
                      <CheckCircle2 className="size-4" aria-hidden /> Resolver manualmente
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

export function AlertsPage() {
  const [params, setParams] = useSearchParams();
  const { hasRole } = useAuth();
  const queryClient = useQueryClient();
  const [ruleDialog, setRuleDialog] = useState<{ open: boolean; rule?: AlertRule | null }>({ open: false });
  const [ruleToDelete, setRuleToDelete] = useState<AlertRule | null>(null);

  const filters = {
    active: params.get("estado") === "resolvidos" ? false : params.get("estado") === "todos" ? undefined : true,
    severity: (params.get("severidade") ?? "") as AlertSeverity | "",
    status: (params.get("status") ?? "") as AlertStatus | "",
    page: Number(params.get("page") ?? 0),
  };
  const selectedId = params.get("alerta");
  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k !== "page" && k !== "alerta") next.delete("page");
    setParams(next, { replace: true });
  };

  const summary = useQuery({ queryKey: ["alerts", "summary"], queryFn: alertsApi.summary, refetchInterval: 30_000 });
  const alerts = useQuery({
    queryKey: ["alerts", "list", filters],
    queryFn: () =>
      alertsApi.list({
        active: filters.active,
        severity: filters.severity || undefined,
        status: filters.status || undefined,
        page: filters.page,
        size: 20,
      }),
    refetchInterval: 30_000,
    placeholderData: (p) => p,
  });
  const rules = useQuery({ queryKey: ["alerts", "rules"], queryFn: alertsApi.rules });
  const selected = useQuery({
    queryKey: ["alerts", "detail", selectedId],
    queryFn: () => alertsApi.get(selectedId!),
    enabled: !!selectedId,
    refetchInterval: 15_000,
  });

  const toggleRule = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => alertsApi.updateRule(id, { enabled }),
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: ["alerts"] });
      toast.success(
        r.enabled ? "Regra ativada." : "Regra desativada. Alertas que ela mantinha abertos foram resolvidos.",
      );
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao atualizar regra."),
  });
  const deleteRule = useMutation({
    mutationFn: (id: string) => alertsApi.deleteRule(id),
    onSuccess: () => {
      setRuleToDelete(null);
      queryClient.invalidateQueries({ queryKey: ["alerts"] });
      toast.success("Regra removida.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao remover regra."),
  });

  const s = summary.data;
  const items = alerts.data?.items ?? [];

  return (
    <>
      <PageHeader
        title="Alertas"
        description="Disparados pelas transições de estado e pelas métricas coletadas. Um alerta permanece único enquanto a condição persistir."
        actions={
          hasRole("ADMIN") && (
            <Button onClick={() => setRuleDialog({ open: true, rule: null })}>
              <Plus className="size-4" aria-hidden /> Nova regra
            </Button>
          )
        }
      />

      <section className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <KpiCard title="Abertos" value={s?.open} icon={BellRing} tone="down" loading={summary.isLoading} />
        <KpiCard title="Reconhecidos" value={s?.acknowledged} icon={Check} tone="default" loading={summary.isLoading} />
        <KpiCard
          title="Críticos ativos"
          value={s?.critical}
          icon={ShieldAlert}
          tone="down"
          loading={summary.isLoading}
        />
        <KpiCard title="Altos ativos" value={s?.high} icon={ShieldAlert} tone="degraded" loading={summary.isLoading} />
        <KpiCard
          title="Atenção ativos"
          value={s?.warning}
          icon={ShieldAlert}
          tone="degraded"
          loading={summary.isLoading}
        />
        <KpiCard
          title="Informativos ativos"
          value={s?.info}
          icon={ShieldAlert}
          tone="info"
          loading={summary.isLoading}
        />
      </section>

      <Tabs defaultValue={params.get("aba") === "regras" ? "rules" : "alerts"}>
        <TabsList>
          <TabsTrigger value="alerts">Alertas</TabsTrigger>
          <TabsTrigger value="rules">Regras ({rules.data?.length ?? 0})</TabsTrigger>
        </TabsList>

        <TabsContent value="alerts">
          <Card className="gap-0 py-0">
            <div className="flex flex-col gap-2 border-b border-border p-3 sm:flex-row">
              <Select
                value={params.get("estado") ?? "ativos"}
                onValueChange={(v) => setParam("estado", v === "ativos" ? "" : v)}
              >
                <SelectTrigger className="w-full sm:w-[170px]" aria-label="Estado">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ativos">Ativos</SelectItem>
                  <SelectItem value="resolvidos">Resolvidos</SelectItem>
                  <SelectItem value="todos">Todos</SelectItem>
                </SelectContent>
              </Select>
              <Select value={filters.severity || ALL} onValueChange={(v) => setParam("severidade", v === ALL ? "" : v)}>
                <SelectTrigger className="w-full sm:w-[170px]" aria-label="Severidade">
                  <SelectValue placeholder="Severidade" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>Todas as severidades</SelectItem>
                  <SelectItem value="CRITICAL">Crítico</SelectItem>
                  <SelectItem value="HIGH">Alto</SelectItem>
                  <SelectItem value="WARNING">Atenção</SelectItem>
                  <SelectItem value="INFO">Informativo</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {alerts.isLoading ? (
              <div className="space-y-2 p-4">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : items.length === 0 ? (
              <div className="p-4">
                <EmptyState
                  icon={BellRing}
                  title="Nenhum alerta"
                  description={
                    filters.active
                      ? "Nenhuma condição de alerta ativa no momento."
                      : "Nenhum alerta corresponde aos filtros."
                  }
                />
              </div>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Severidade</TableHead>
                      <TableHead>Alerta</TableHead>
                      <TableHead>Recurso</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Disparado</TableHead>
                      <TableHead>Duração</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((a) => (
                      <TableRow
                        key={a.id}
                        className="cursor-pointer"
                        tabIndex={0}
                        onClick={() => setParam("alerta", a.id)}
                        onKeyDown={(e) => e.key === "Enter" && setParam("alerta", a.id)}
                      >
                        <TableCell>
                          <SeverityBadge severity={a.severity} />
                        </TableCell>
                        <TableCell>
                          <p className="font-medium">{a.title}</p>
                          <p className="max-w-[420px] truncate text-xs text-muted-foreground">
                            {a.message ?? a.metricLabel}
                          </p>
                        </TableCell>
                        <TableCell className="text-sm">{a.resourceName ?? "—"}</TableCell>
                        <TableCell>
                          <AlertStatusBadge status={a.status} />
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-sm text-muted-foreground" title={a.triggeredAt}>
                          {formatRelative(a.triggeredAt)}
                        </TableCell>
                        <TableCell className="text-sm tabular-nums">
                          {a.durationSeconds != null ? formatDuration(a.durationSeconds) : "—"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
            {alerts.data && alerts.data.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-border p-3 text-sm text-muted-foreground">
                <span>
                  Página {alerts.data.page + 1} de {alerts.data.totalPages} · {alerts.data.totalItems} alertas
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={alerts.data.page === 0}
                    onClick={() => setParam("page", String(alerts.data!.page - 1))}
                  >
                    Anterior
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={alerts.data.page + 1 >= alerts.data.totalPages}
                    onClick={() => setParam("page", String(alerts.data!.page + 1))}
                  >
                    Próxima
                  </Button>
                </div>
              </div>
            )}
          </Card>
        </TabsContent>

        <TabsContent value="rules">
          <Card>
            <CardHeader>
              <CardTitle>Regras de alerta</CardTitle>
              <CardDescription>
                Condições numéricas exigem ocorrências consecutivas e duração mínima antes de disparar; uma leitura
                normal resolve o alerta.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {rules.isLoading ? (
                <Skeleton className="h-32 w-full" />
              ) : (rules.data?.length ?? 0) === 0 ? (
                <EmptyState
                  compact
                  icon={ShieldAlert}
                  title="Nenhuma regra"
                  description="Crie regras para receber alertas."
                />
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Ativa</TableHead>
                        <TableHead>Regra</TableHead>
                        <TableHead>Condição</TableHead>
                        <TableHead>Severidade</TableHead>
                        <TableHead>Escopo</TableHead>
                        <TableHead>Alertas ativos</TableHead>
                        {hasRole("ADMIN") && <TableHead className="text-right">Ações</TableHead>}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {rules.data!.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell>
                            <Switch
                              checked={r.enabled}
                              disabled={!hasRole("ADMIN") || toggleRule.isPending}
                              onCheckedChange={(enabled) => toggleRule.mutate({ id: r.id, enabled })}
                              aria-label={`Ativar regra ${r.name}`}
                            />
                          </TableCell>
                          <TableCell>
                            <p className="font-medium">{r.name}</p>
                            {r.description && (
                              <p className="max-w-[360px] truncate text-xs text-muted-foreground">{r.description}</p>
                            )}
                          </TableCell>
                          <TableCell className="text-sm">
                            {r.metricLabel}
                            {r.operator && r.threshold != null && (
                              <span className="text-muted-foreground">
                                {" "}
                                {r.operator === "GT"
                                  ? ">"
                                  : r.operator === "GTE"
                                    ? "≥"
                                    : r.operator === "LT"
                                      ? "<"
                                      : "≤"}{" "}
                                {r.threshold} {r.unit}
                                {r.occurrences > 1 ? ` · ${r.occurrences}×` : ""}
                                {r.durationSeconds > 0 ? ` · ${formatDuration(r.durationSeconds)}` : ""}
                              </span>
                            )}
                          </TableCell>
                          <TableCell>
                            <SeverityBadge severity={r.severity} />
                          </TableCell>
                          <TableCell className="text-sm text-muted-foreground">
                            {r.resourceName
                              ? `Recurso: ${r.resourceName}`
                              : r.groupName
                                ? `Grupo: ${r.groupName}`
                                : "Todos os recursos"}
                          </TableCell>
                          <TableCell>
                            {r.activeAlerts > 0 ? (
                              <Badge variant="destructive">{r.activeAlerts}</Badge>
                            ) : (
                              <span className="text-sm text-muted-foreground">0</span>
                            )}
                          </TableCell>
                          {hasRole("ADMIN") && (
                            <TableCell className="text-right">
                              <Button
                                variant="ghost"
                                size="icon"
                                aria-label={`Editar ${r.name}`}
                                onClick={() => setRuleDialog({ open: true, rule: r })}
                              >
                                <Pencil className="size-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                aria-label={`Remover ${r.name}`}
                                onClick={() => setRuleToDelete(r)}
                              >
                                <Trash2 className="size-4" />
                              </Button>
                            </TableCell>
                          )}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <AlertDetail alert={selectedId ? (selected.data ?? null) : null} onClose={() => setParam("alerta", "")} />
      <AlertRuleDialog
        open={ruleDialog.open}
        rule={ruleDialog.rule}
        onOpenChange={(o) => setRuleDialog({ open: o, rule: o ? ruleDialog.rule : null })}
      />
      <ConfirmDialog
        open={!!ruleToDelete}
        onOpenChange={(o) => !o && setRuleToDelete(null)}
        title="Remover regra"
        description={`A regra "${ruleToDelete?.name}" será removida e os alertas que ela mantém abertos serão resolvidos.`}
        confirmLabel="Remover"
        destructive
        pending={deleteRule.isPending}
        onConfirm={() => ruleToDelete && deleteRule.mutate(ruleToDelete.id)}
      />
    </>
  );
}
