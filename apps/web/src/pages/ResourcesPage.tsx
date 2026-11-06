import { useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { type ColumnDef, flexRender, getCoreRowModel, useReactTable } from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ArrowUpDown, Plus, Search, Server, X } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { ResourceFormDialog } from "@/features/resources/ResourceFormDialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { groupsApi, resourcesApi } from "@/services/endpoints";
import { formatInterval, formatMs, formatRelative } from "@/lib/format";
import type { Resource } from "@/types/api";

const ALL = "all";
const STATUSES = [
  { value: "UP", label: "Disponível" },
  { value: "DOWN", label: "Indisponível" },
  { value: "DEGRADED", label: "Degradado" },
  { value: "PENDING", label: "Aguardando verificação" },
  { value: "UNKNOWN", label: "Sem dados" },
  { value: "MAINTENANCE", label: "Em manutenção" },
];
const TYPES = [
  { value: "HTTP", label: "Serviço HTTP" },
  { value: "HTTPS", label: "Serviço HTTPS" },
  { value: "HEALTH_ENDPOINT", label: "Aplicação com health check" },
  { value: "AGENT_SERVER", label: "Servidor com agente" },
];
const ENVS = [
  { value: "DEVELOPMENT", label: "Desenvolvimento" },
  { value: "STAGING", label: "Homologação" },
  { value: "PRODUCTION", label: "Produção" },
];

export function ResourcesPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [formOpen, setFormOpen] = useState(params.get("novo") === "1");

  const filters = {
    search: params.get("search") ?? "",
    status: params.get("status") ?? "",
    type: params.get("type") ?? "",
    environment: params.get("environment") ?? "",
    groupId: params.get("groupId") ?? "",
    page: Number(params.get("page") ?? 0),
    sort: params.get("sort") ?? "name",
    direction: (params.get("direction") as "asc" | "desc") ?? "asc",
  };

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    next.delete("novo");
    setParams(next, { replace: true });
  };

  const query = useQuery({
    queryKey: ["resources", "list", filters],
    queryFn: ({ signal }) => resourcesApi.list({ ...filters, size: 20 }, signal),
    refetchInterval: 15_000,
    placeholderData: (prev) => prev,
  });
  const groups = useQuery({ queryKey: ["groups"], queryFn: groupsApi.list });

  const toggleSort = (column: string) => {
    const next = new URLSearchParams(params);
    if (filters.sort === column) next.set("direction", filters.direction === "asc" ? "desc" : "asc");
    else {
      next.set("sort", column);
      next.set("direction", "asc");
    }
    setParams(next, { replace: true });
  };

  const SortIcon = ({ column }: { column: string }) =>
    filters.sort !== column ? (
      <ArrowUpDown className="size-3.5 text-muted-foreground" aria-hidden />
    ) : filters.direction === "asc" ? (
      <ArrowUp className="size-3.5" aria-hidden />
    ) : (
      <ArrowDown className="size-3.5" aria-hidden />
    );

  const columns = useMemo<ColumnDef<Resource>[]>(
    () => [
      {
        id: "status",
        header: () => (
          <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort("status")}>
            Status <SortIcon column="status" />
          </button>
        ),
        cell: ({ row }) => <StatusBadge status={row.original.status} size="sm" pulse />,
      },
      {
        id: "name",
        header: () => (
          <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort("name")}>
            Recurso <SortIcon column="name" />
          </button>
        ),
        cell: ({ row }) => (
          <div className="min-w-0">
            <Link to={`/recursos/${row.original.id}`} className="font-medium hover:underline">
              {row.original.name}
            </Link>
            <p className="max-w-[320px] truncate text-xs text-muted-foreground">{row.original.targetUrl}</p>
            {row.original.tags.length > 0 && (
              <div className="mt-1 flex flex-wrap gap-1">
                {row.original.tags.map((t) => (
                  <Badge key={t} variant="secondary" className="px-1.5 py-0 text-[10px]">
                    {t}
                  </Badge>
                ))}
              </div>
            )}
          </div>
        ),
      },
      {
        id: "type",
        header: "Tipo",
        cell: ({ row }) => <span className="text-sm text-muted-foreground">{row.original.typeLabel}</span>,
      },
      {
        id: "environment",
        header: () => (
          <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort("environment")}>
            Ambiente <SortIcon column="environment" />
          </button>
        ),
        cell: ({ row }) => <span className="text-sm">{row.original.environmentLabel}</span>,
      },
      {
        id: "group",
        header: "Grupo",
        cell: ({ row }) => <span className="text-sm text-muted-foreground">{row.original.group?.name ?? "—"}</span>,
      },
      {
        id: "responseTime",
        header: () => (
          <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort("responseTime")}>
            Resposta <SortIcon column="responseTime" />
          </button>
        ),
        cell: ({ row }) => (
          <span className="font-medium tabular-nums">{formatMs(row.original.lastResponseTimeMs)}</span>
        ),
      },
      {
        id: "interval",
        header: "Intervalo",
        cell: ({ row }) => (
          <span className="text-sm text-muted-foreground">
            {row.original.check ? formatInterval(row.original.check.intervalSeconds) : "—"}
            {!row.original.enabled && (
              <Badge variant="outline" className="ml-2 text-[10px]">
                Pausado
              </Badge>
            )}
          </span>
        ),
      },
      {
        id: "lastCheckAt",
        header: () => (
          <button type="button" className="inline-flex items-center gap-1" onClick={() => toggleSort("lastCheckAt")}>
            Última verificação <SortIcon column="lastCheckAt" />
          </button>
        ),
        cell: ({ row }) => (
          <span className="text-sm text-muted-foreground" title={row.original.lastCheckAt ?? undefined}>
            {row.original.lastCheckAt ? formatRelative(row.original.lastCheckAt) : "ainda não verificado"}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [filters.sort, filters.direction],
  );

  const data = query.data?.items ?? [];
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualSorting: true,
    manualPagination: true,
  });
  const hasFilters = !!(filters.search || filters.status || filters.type || filters.environment || filters.groupId);

  return (
    <>
      <PageHeader
        title="Recursos Monitorados"
        description="Serviços e servidores autorizados verificados pelo InfraWatch."
        actions={
          hasRole("ADMIN") && (
            <Button onClick={() => setFormOpen(true)}>
              <Plus className="size-4" aria-hidden /> Novo recurso
            </Button>
          )
        }
      />

      <Card className="gap-0 py-0">
        <div className="flex flex-col gap-2 border-b border-border p-3 md:flex-row md:items-center">
          <div className="relative flex-1">
            <Search
              className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden
            />
            <Input
              aria-label="Buscar por nome ou URL"
              placeholder="Buscar por nome ou URL…"
              className="pl-8"
              defaultValue={filters.search}
              onChange={(e) => {
                const value = e.target.value;
                window.clearTimeout((window as unknown as { __iwSearch?: number }).__iwSearch);
                (window as unknown as { __iwSearch?: number }).__iwSearch = window.setTimeout(
                  () => setParam("search", value),
                  300,
                );
              }}
            />
          </div>
          <div className="grid grid-cols-2 gap-2 sm:flex">
            <Select value={filters.status || ALL} onValueChange={(v) => setParam("status", v === ALL ? "" : v)}>
              <SelectTrigger className="w-full sm:w-[170px]" aria-label="Filtrar por status">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Todos os status</SelectItem>
                {STATUSES.map((s) => (
                  <SelectItem key={s.value} value={s.value}>
                    {s.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={filters.type || ALL} onValueChange={(v) => setParam("type", v === ALL ? "" : v)}>
              <SelectTrigger className="w-full sm:w-[190px]" aria-label="Filtrar por tipo">
                <SelectValue placeholder="Tipo" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Todos os tipos</SelectItem>
                {TYPES.map((s) => (
                  <SelectItem key={s.value} value={s.value}>
                    {s.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={filters.environment || ALL}
              onValueChange={(v) => setParam("environment", v === ALL ? "" : v)}
            >
              <SelectTrigger className="w-full sm:w-[160px]" aria-label="Filtrar por ambiente">
                <SelectValue placeholder="Ambiente" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Todos os ambientes</SelectItem>
                {ENVS.map((s) => (
                  <SelectItem key={s.value} value={s.value}>
                    {s.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={filters.groupId || ALL} onValueChange={(v) => setParam("groupId", v === ALL ? "" : v)}>
              <SelectTrigger className="w-full sm:w-[160px]" aria-label="Filtrar por grupo">
                <SelectValue placeholder="Grupo" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Todos os grupos</SelectItem>
                {(groups.data ?? []).map((g) => (
                  <SelectItem key={g.id} value={g.id}>
                    {g.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {hasFilters && (
              <Button variant="ghost" size="sm" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
                <X className="size-4" aria-hidden /> Limpar
              </Button>
            )}
          </div>
        </div>

        {query.isLoading ? (
          <div className="space-y-2 p-4">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : data.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={Server}
              title={hasFilters ? "Nenhum recurso corresponde aos filtros" : "Nenhum recurso cadastrado"}
              description={
                hasFilters
                  ? "Ajuste ou limpe os filtros para ver outros recursos."
                  : "Cadastre um serviço HTTP autorizado para iniciar o monitoramento."
              }
              action={
                hasFilters ? (
                  <Button variant="outline" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
                    Limpar filtros
                  </Button>
                ) : hasRole("ADMIN") ? (
                  <Button onClick={() => setFormOpen(true)}>
                    <Plus className="size-4" aria-hidden /> Cadastrar recurso
                  </Button>
                ) : undefined
              }
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((hg) => (
                  <TableRow key={hg.id}>
                    {hg.headers.map((h) => (
                      <TableHead key={h.id} className="whitespace-nowrap">
                        {h.isPlaceholder ? null : flexRender(h.column.columnDef.header, h.getContext())}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    tabIndex={0}
                    onClick={() => navigate(`/recursos/${row.original.id}`)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") navigate(`/recursos/${row.original.id}`);
                    }}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}

        {query.data && query.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border p-3 text-sm text-muted-foreground">
            <span>
              Página {query.data.page + 1} de {query.data.totalPages} · {query.data.totalItems} recursos
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={query.data.page === 0}
                onClick={() => setParam("page", String(query.data!.page - 1))}
              >
                Anterior
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={query.data.page + 1 >= query.data.totalPages}
                onClick={() => setParam("page", String(query.data!.page + 1))}
              >
                Próxima
              </Button>
            </div>
          </div>
        )}
      </Card>

      <ResourceFormDialog
        open={formOpen}
        onOpenChange={(open) => {
          setFormOpen(open);
          if (!open && params.get("novo")) setParam("novo", "");
        }}
        onSaved={(r) => navigate(`/recursos/${r.id}`)}
      />
    </>
  );
}
