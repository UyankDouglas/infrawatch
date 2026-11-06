import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { CircleCheck, Plus, Search, Siren, Timer, UserRound } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { KpiCard } from "@/features/dashboard/KpiCard";
import { SeverityBadge } from "@/features/alerts/SeverityBadge";
import { IncidentStatusBadge } from "@/features/incidents/IncidentBadges";
import { IncidentFormDialog } from "@/features/incidents/IncidentFormDialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { incidentsApi } from "@/services/endpoints";
import { formatDuration, formatRelative } from "@/lib/format";

const ALL = "all";

export function IncidentsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [createOpen, setCreateOpen] = useState(params.get("novo") === "1");

  const estado = params.get("estado") ?? "ativos";
  const filters = {
    active: estado === "ativos" ? true : estado === "encerrados" ? false : undefined,
    severity: params.get("severidade") ?? "",
    origin: params.get("origem") ?? "",
    page: Number(params.get("page") ?? 0),
  };
  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k !== "page") next.delete("page");
    next.delete("novo");
    setParams(next, { replace: true });
  };

  const summary = useQuery({
    queryKey: ["incidents", "summary"],
    queryFn: incidentsApi.summary,
    refetchInterval: 30_000,
  });
  const list = useQuery({
    queryKey: ["incidents", "list", filters],
    queryFn: () =>
      incidentsApi.list({
        active: filters.active,
        severity: filters.severity || undefined,
        origin: filters.origin || undefined,
        page: filters.page,
        size: 20,
      }),
    refetchInterval: 30_000,
    placeholderData: (p) => p,
  });

  const s = summary.data;
  const items = list.data?.items ?? [];

  return (
    <>
      <PageHeader
        title="Incidentes"
        description="Abertos automaticamente quando um recurso é confirmado como indisponível, ou manualmente pela equipe. A timeline preserva os eventos reais."
        actions={
          hasRole("OPERATOR") && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="size-4" aria-hidden /> Abrir incidente
            </Button>
          )
        }
      />

      <section className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <KpiCard title="Abertos" value={s?.open} icon={Siren} tone="down" loading={summary.isLoading} />
        <KpiCard
          title="Reconhecidos"
          value={s?.acknowledged}
          icon={UserRound}
          tone="default"
          loading={summary.isLoading}
        />
        <KpiCard
          title="Em investigação"
          value={s?.investigating}
          icon={Search}
          tone="degraded"
          loading={summary.isLoading}
        />
        <KpiCard title="Críticos ativos" value={s?.critical} icon={Siren} tone="down" loading={summary.isLoading} />
        <KpiCard
          title="Resolvidos (24 h)"
          value={s?.resolvedLast24h}
          icon={CircleCheck}
          tone="up"
          loading={summary.isLoading}
        />
        <KpiCard
          title="MTTR (24 h)"
          value={s?.mttrSeconds == null ? null : formatDuration(Math.round(s.mttrSeconds))}
          icon={Timer}
          tone="info"
          loading={summary.isLoading}
          emptyLabel="Sem dados suficientes"
          hint="Média abertura → resolução"
        />
      </section>

      <Card className="gap-0 py-0">
        <div className="flex flex-col gap-2 border-b border-border p-3 sm:flex-row">
          <Select value={estado} onValueChange={(v) => setParam("estado", v === "ativos" ? "" : v)}>
            <SelectTrigger className="w-full sm:w-[170px]" aria-label="Estado">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ativos">Ativos</SelectItem>
              <SelectItem value="encerrados">Resolvidos e encerrados</SelectItem>
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
          <Select value={filters.origin || ALL} onValueChange={(v) => setParam("origem", v === ALL ? "" : v)}>
            <SelectTrigger className="w-full sm:w-[170px]" aria-label="Origem">
              <SelectValue placeholder="Origem" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Todas as origens</SelectItem>
              <SelectItem value="AUTOMATIC">Automático</SelectItem>
              <SelectItem value="MANUAL">Manual</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {list.isLoading ? (
          <div className="space-y-2 p-4">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={Siren}
              title="Nenhum incidente"
              description={
                filters.active ? "Nenhum incidente ativo no momento." : "Nenhum incidente corresponde aos filtros."
              }
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>#</TableHead>
                  <TableHead>Severidade</TableHead>
                  <TableHead>Incidente</TableHead>
                  <TableHead>Recurso</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Responsável</TableHead>
                  <TableHead>Aberto</TableHead>
                  <TableHead>Duração</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((i) => (
                  <TableRow
                    key={i.id}
                    className="cursor-pointer"
                    tabIndex={0}
                    onClick={() => navigate(`/incidentes/${i.id}`)}
                    onKeyDown={(e) => e.key === "Enter" && navigate(`/incidentes/${i.id}`)}
                  >
                    <TableCell className="font-mono text-xs text-muted-foreground">#{i.number}</TableCell>
                    <TableCell>
                      <SeverityBadge severity={i.severity} />
                    </TableCell>
                    <TableCell>
                      <Link
                        to={`/incidentes/${i.id}`}
                        className="font-medium hover:underline"
                        onClick={(e) => e.stopPropagation()}
                      >
                        {i.title}
                      </Link>
                      <p className="text-xs text-muted-foreground">
                        {i.originLabel}
                        {i.commentCount > 0 ? ` · ${i.commentCount} comentário(s)` : ""}
                      </p>
                    </TableCell>
                    <TableCell className="text-sm">{i.resourceName ?? "—"}</TableCell>
                    <TableCell>
                      <IncidentStatusBadge status={i.status} />
                    </TableCell>
                    <TableCell className="text-sm">
                      {i.assignee?.name ?? <span className="text-muted-foreground">—</span>}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-sm text-muted-foreground" title={i.openedAt}>
                      {formatRelative(i.openedAt)}
                    </TableCell>
                    <TableCell className="text-sm tabular-nums">
                      {i.durationSeconds != null ? formatDuration(i.durationSeconds) : "—"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        {list.data && list.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border p-3 text-sm text-muted-foreground">
            <span>
              Página {list.data.page + 1} de {list.data.totalPages} · {list.data.totalItems} incidentes
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={list.data.page === 0}
                onClick={() => setParam("page", String(list.data!.page - 1))}
              >
                Anterior
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={list.data.page + 1 >= list.data.totalPages}
                onClick={() => setParam("page", String(list.data!.page + 1))}
              >
                Próxima
              </Button>
            </div>
          </div>
        )}
      </Card>

      <IncidentFormDialog
        open={createOpen}
        onOpenChange={(o) => {
          setCreateOpen(o);
          if (!o && params.get("novo")) setParam("novo", "");
        }}
        onCreated={(i) => navigate(`/incidentes/${i.id}`)}
      />
    </>
  );
}
