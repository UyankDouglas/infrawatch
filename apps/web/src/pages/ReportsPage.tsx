import { useState } from "react";
import { Link } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  ChartColumnBig,
  Clock,
  Download,
  FileSpreadsheet,
  FileText,
  Info,
  Loader2,
  Siren,
  Timer,
} from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/PageHeader";
import { PeriodSelect } from "@/components/PeriodSelect";
import { EmptyState } from "@/components/EmptyState";
import { StatusBadge } from "@/components/StatusBadge";
import { KpiCard } from "@/features/dashboard/KpiCard";
import { SeverityBadge } from "@/features/alerts/SeverityBadge";
import { AvailabilityBars, IncidentsBySeverity } from "@/features/reports/ReportCharts";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { usePeriod } from "@/hooks/usePeriod";
import { ApiError } from "@/services/api";
import { groupsApi, reportsApi } from "@/services/endpoints";
import { formatBytes, formatDateTime, formatDuration, formatMs, formatPercent } from "@/lib/format";
import type { ExportFormat, ExportType, MaintenanceMode } from "@/types/reports";

const ALL = "__all__";

function seconds(v: number | null | undefined): string {
  return v == null ? "—" : formatDuration(Math.round(v));
}

export function ReportsPage() {
  const queryClient = useQueryClient();
  const [period, setPeriod] = usePeriod("7d");
  const [maintenance, setMaintenance] = useState<MaintenanceMode>("excluded");
  const [groupId, setGroupId] = useState<string>(ALL);
  const [format, setFormat] = useState<ExportFormat>("CSV");
  const group = groupId === ALL ? undefined : groupId;

  const groups = useQuery({ queryKey: ["groups"], queryFn: groupsApi.list });
  const availability = useQuery({
    queryKey: ["reports", "availability", period, maintenance, group],
    queryFn: () => reportsApi.availability(period, maintenance, group),
  });
  const incidents = useQuery({
    queryKey: ["reports", "incidents", period],
    queryFn: () => reportsApi.incidents(period),
  });
  const exportsHistory = useQuery({ queryKey: ["reports", "exports"], queryFn: reportsApi.exports });

  const createExport = useMutation({
    mutationFn: (type: ExportType) => reportsApi.createExport({ type, format, period, maintenance, groupId: group }),
    onSuccess: (e) => {
      queryClient.invalidateQueries({ queryKey: ["reports", "exports"] });
      toast.success(`Exportação gerada: ${e.fileName} (${e.rowCount} linha(s)).`, {
        action: { label: "Baixar", onClick: () => window.open(reportsApi.downloadUrl(e.id), "_blank") },
      });
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao gerar exportação."),
  });

  const av = availability.data;
  const inc = incidents.data;

  return (
    <>
      <PageHeader
        title="Relatórios"
        description="Disponibilidade, incidentes e latência calculados a partir dos dados persistidos, com definições explícitas."
        actions={
          <>
            <PeriodSelect value={period} onChange={setPeriod} />
            <Select value={maintenance} onValueChange={(v) => setMaintenance(v as MaintenanceMode)}>
              <SelectTrigger className="w-[230px]" aria-label="Tratamento de manutenções">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="excluded">Manutenções excluídas</SelectItem>
                <SelectItem value="counted">Manutenções contam como indisponibilidade</SelectItem>
              </SelectContent>
            </Select>
            <Select value={groupId} onValueChange={setGroupId}>
              <SelectTrigger className="w-[180px]" aria-label="Grupo">
                <SelectValue placeholder="Todos os grupos" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Todos os grupos</SelectItem>
                {groups.data?.map((g) => (
                  <SelectItem key={g.id} value={g.id}>
                    {g.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          title="Disponibilidade geral"
          icon={Activity}
          tone="up"
          value={av && av.overallAvailabilityPercent != null ? formatPercent(av.overallAvailabilityPercent) : null}
          hint={av?.maintenanceMode === "excluded" ? "manutenções excluídas" : "manutenções contadas"}
          loading={availability.isLoading}
        />
        <KpiCard
          title="Tempo indisponível (soma)"
          icon={Clock}
          tone="down"
          value={av ? seconds(av.totalUnavailableSeconds) : null}
          hint="estado Indisponível, todos os recursos"
          loading={availability.isLoading}
        />
        <KpiCard
          title="Incidentes no período"
          icon={Siren}
          tone="degraded"
          value={inc ? inc.total : null}
          hint={inc ? `${inc.automatic} automáticos · ${inc.manual} manuais` : undefined}
          loading={incidents.isLoading}
        />
        <KpiCard
          title="MTTR"
          icon={Timer}
          tone="info"
          value={inc && inc.mttrSeconds != null ? seconds(inc.mttrSeconds) : null}
          emptyLabel="Sem incidentes resolvidos"
          hint={inc?.mttaSeconds != null ? `MTTA ${seconds(inc.mttaSeconds)}` : "média de resolução"}
          loading={incidents.isLoading}
        />
      </div>

      <Tabs defaultValue="availability">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <TabsList>
            <TabsTrigger value="availability">Disponibilidade</TabsTrigger>
            <TabsTrigger value="incidents">Incidentes</TabsTrigger>
            <TabsTrigger value="exports">Exportações</TabsTrigger>
          </TabsList>
          <div className="flex items-center gap-2">
            <Select value={format} onValueChange={(v) => setFormat(v as ExportFormat)}>
              <SelectTrigger className="w-[110px]" aria-label="Formato de exportação">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="CSV">CSV</SelectItem>
                <SelectItem value="XLSX">XLSX</SelectItem>
              </SelectContent>
            </Select>
            <Button
              variant="outline"
              onClick={() => createExport.mutate("AVAILABILITY")}
              disabled={createExport.isPending}
            >
              {createExport.isPending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <Download className="size-4" aria-hidden />
              )}
              Exportar disponibilidade
            </Button>
            <Button
              variant="outline"
              onClick={() => createExport.mutate("INCIDENTS")}
              disabled={createExport.isPending}
            >
              <Download className="size-4" aria-hidden /> Exportar incidentes
            </Button>
            <Button variant="outline" onClick={() => createExport.mutate("CHECKS")} disabled={createExport.isPending}>
              <Download className="size-4" aria-hidden /> Exportar verificações
            </Button>
          </div>
        </div>

        <TabsContent value="availability" className="space-y-4">
          {av && (
            <p className="flex items-start gap-2 rounded-md border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
              <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              <span>
                {av.formula} MTTR = média de (resolvido − aberto) dos incidentes resolvidos; MTBF = média do intervalo
                entre o fim de um incidente e o início do seguinte (exige ao menos 2 incidentes). Janela:{" "}
                {formatDateTime(av.from, "dd/MM HH:mm")} → {formatDateTime(av.to, "dd/MM HH:mm")}.
              </span>
            </p>
          )}
          {availability.isLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : !av || av.rows.length === 0 ? (
            <EmptyState
              icon={ChartColumnBig}
              title="Sem recursos para relatar"
              description="Cadastre recursos monitorados para gerar relatórios."
            />
          ) : (
            <>
              <Card>
                <CardHeader>
                  <CardTitle>Disponibilidade por recurso</CardTitle>
                  <CardDescription>Percentual sobre o tempo observado com estado conhecido.</CardDescription>
                </CardHeader>
                <CardContent>
                  <AvailabilityBars rows={av.rows} />
                </CardContent>
              </Card>
              <Card>
                <CardContent className="overflow-x-auto p-0">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Recurso</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead className="text-right">Disponibilidade</TableHead>
                        <TableHead className="text-right">Indisponível</TableHead>
                        <TableHead className="text-right">Manutenção</TableHead>
                        <TableHead className="text-right">Verificações</TableHead>
                        <TableHead className="text-right">Latência média / p95 / máx</TableHead>
                        <TableHead className="text-right">Incidentes</TableHead>
                        <TableHead className="text-right">MTTR</TableHead>
                        <TableHead className="text-right">MTBF</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {av.rows.map((r) => (
                        <TableRow key={r.resourceId}>
                          <TableCell>
                            <Link to={`/recursos/${r.resourceId}`} className="font-medium hover:underline">
                              {r.name}
                            </Link>
                            <p className="text-xs text-muted-foreground">
                              {r.typeLabel} · {r.environmentLabel}
                              {r.groupName ? ` · ${r.groupName}` : ""}
                            </p>
                          </TableCell>
                          <TableCell>
                            <StatusBadge status={r.currentStatus} size="sm" />
                          </TableCell>
                          <TableCell className="text-right font-medium tabular-nums">
                            {r.availabilityPercent == null ? (
                              <Tooltip>
                                <TooltipTrigger className="text-muted-foreground">sem dados</TooltipTrigger>
                                <TooltipContent>Nenhum intervalo com estado conhecido no período.</TooltipContent>
                              </Tooltip>
                            ) : (
                              formatPercent(r.availabilityPercent)
                            )}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{seconds(r.unavailableSeconds)}</TableCell>
                          <TableCell className="text-right tabular-nums">{seconds(r.maintenanceSeconds)}</TableCell>
                          <TableCell className="text-right tabular-nums">
                            {r.checks}
                            {r.failedChecks > 0 && <span className="text-destructive"> ({r.failedChecks} falhas)</span>}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {formatMs(r.avgResponseMs)} / {formatMs(r.p95ResponseMs)} / {formatMs(r.maxResponseMs)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{r.incidents}</TableCell>
                          <TableCell className="text-right tabular-nums">{seconds(r.mttrSeconds)}</TableCell>
                          <TableCell className="text-right tabular-nums">
                            {r.mtbfSeconds != null ? (
                              seconds(r.mtbfSeconds)
                            ) : (
                              <Tooltip>
                                <TooltipTrigger className="text-muted-foreground">—</TooltipTrigger>
                                <TooltipContent>{r.mtbfNote ?? "Indisponível"}</TooltipContent>
                              </Tooltip>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </>
          )}
        </TabsContent>

        <TabsContent value="incidents" className="space-y-4">
          {incidents.isLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : !inc ? null : (
            <>
              <p className="text-xs text-muted-foreground">{inc.note}</p>
              <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
                <Card>
                  <CardHeader>
                    <CardTitle>Por severidade</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <IncidentsBySeverity report={inc} />
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="overflow-x-auto p-0">
                    {inc.rows.length === 0 ? (
                      <EmptyState compact icon={FileText} title="Nenhum incidente no período" />
                    ) : (
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>#</TableHead>
                            <TableHead>Título</TableHead>
                            <TableHead>Severidade</TableHead>
                            <TableHead>Status</TableHead>
                            <TableHead>Origem</TableHead>
                            <TableHead>Recurso</TableHead>
                            <TableHead>Aberto em</TableHead>
                            <TableHead className="text-right">Duração</TableHead>
                            <TableHead className="text-right">Até reconhecer</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {inc.rows.map((i) => (
                            <TableRow key={i.id}>
                              <TableCell className="tabular-nums text-muted-foreground">{i.number}</TableCell>
                              <TableCell>
                                <Link to={`/incidentes/${i.id}`} className="font-medium hover:underline">
                                  {i.title}
                                </Link>
                                {i.cause && <p className="text-xs text-muted-foreground">Causa: {i.cause}</p>}
                              </TableCell>
                              <TableCell>
                                <SeverityBadge severity={i.severity} label={i.severityLabel} />
                              </TableCell>
                              <TableCell>
                                <Badge variant="secondary">{i.statusLabel}</Badge>
                              </TableCell>
                              <TableCell className="text-muted-foreground">{i.originLabel}</TableCell>
                              <TableCell className="text-muted-foreground">{i.resourceName ?? "—"}</TableCell>
                              <TableCell className="whitespace-nowrap text-muted-foreground">
                                {formatDateTime(i.openedAt, "dd/MM HH:mm")}
                              </TableCell>
                              <TableCell className="text-right tabular-nums">{seconds(i.durationSeconds)}</TableCell>
                              <TableCell className="text-right tabular-nums">
                                {seconds(i.timeToAcknowledgeSeconds)}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </CardContent>
                </Card>
              </div>
            </>
          )}
        </TabsContent>

        <TabsContent value="exports">
          <Card>
            <CardHeader>
              <CardTitle>Histórico de exportações</CardTitle>
              <CardDescription>
                Arquivos gerados nesta organização ficam disponíveis para download (últimos 50). Cada arquivo registra o
                período e o tratamento de manutenções usados.
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              {exportsHistory.isLoading ? (
                <Skeleton className="m-6 h-24" />
              ) : (exportsHistory.data?.length ?? 0) === 0 ? (
                <EmptyState
                  compact
                  icon={FileSpreadsheet}
                  title="Nenhuma exportação ainda"
                  description="Use os botões acima para gerar CSV ou XLSX."
                />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Arquivo</TableHead>
                      <TableHead>Tipo</TableHead>
                      <TableHead>Período</TableHead>
                      <TableHead>Manutenções</TableHead>
                      <TableHead className="text-right">Linhas</TableHead>
                      <TableHead className="text-right">Tamanho</TableHead>
                      <TableHead>Gerado por</TableHead>
                      <TableHead>Quando</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {exportsHistory.data!.map((e) => (
                      <TableRow key={e.id}>
                        <TableCell className="font-mono text-xs">{e.fileName}</TableCell>
                        <TableCell>
                          {e.typeLabel} <Badge variant="outline">{e.format}</Badge>
                        </TableCell>
                        <TableCell>{e.period}</TableCell>
                        <TableCell className="text-muted-foreground">
                          {e.maintenanceMode === "excluded" ? "excluídas" : "contadas"}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">{e.rowCount}</TableCell>
                        <TableCell className="text-right tabular-nums">{formatBytes(e.sizeBytes)}</TableCell>
                        <TableCell className="text-muted-foreground">{e.createdBy ?? "—"}</TableCell>
                        <TableCell className="whitespace-nowrap text-muted-foreground">
                          {formatDateTime(e.createdAt)}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button asChild variant="ghost" size="sm">
                            <a href={reportsApi.downloadUrl(e.id)} download={e.fileName}>
                              <Download className="size-4" aria-hidden /> Baixar
                            </a>
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </>
  );
}
