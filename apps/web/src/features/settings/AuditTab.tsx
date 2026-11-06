import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ScrollText, Search } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { auditApi, organizationApi, type AuditFilters } from "@/services/endpoints";
import { formatDateTime } from "@/lib/format";

const ALL = "__all__";

function toInstant(local: string, endOfDay = false): string | undefined {
  if (!local) return undefined;
  const d = new Date(local + (endOfDay ? "T23:59:59" : "T00:00:00"));
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

export function AuditTab() {
  const [action, setAction] = useState(ALL);
  const [userId, setUserId] = useState(ALL);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [search, setSearch] = useState("");
  const [applied, setApplied] = useState<AuditFilters>({});
  const [page, setPage] = useState(0);

  const actions = useQuery({ queryKey: ["audit", "actions"], queryFn: auditApi.actions });
  const members = useQuery({ queryKey: ["members"], queryFn: organizationApi.members });
  const audit = useQuery({
    queryKey: ["audit", applied, page],
    queryFn: () => auditApi.list(page, 50, applied),
  });

  const apply = () => {
    setPage(0);
    setApplied({
      action: action === ALL ? undefined : action,
      userId: userId === ALL ? undefined : userId,
      from: toInstant(from),
      to: toInstant(to, true),
      search: search.trim() || undefined,
    });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Auditoria</CardTitle>
        <CardDescription>
          Ações relevantes registradas nesta organização. Filtre por ação, usuário e período.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form
          className="grid gap-2 md:grid-cols-[1fr_1fr_150px_150px_1fr_auto]"
          onSubmit={(e) => {
            e.preventDefault();
            apply();
          }}
        >
          <Select value={action} onValueChange={setAction}>
            <SelectTrigger aria-label="Ação">
              <SelectValue placeholder="Todas as ações" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Todas as ações</SelectItem>
              {actions.data?.map((a) => (
                <SelectItem key={a} value={a}>
                  {a}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={userId} onValueChange={setUserId}>
            <SelectTrigger aria-label="Usuário">
              <SelectValue placeholder="Todos os usuários" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Todos os usuários</SelectItem>
              {members.data?.map((m) => (
                <SelectItem key={m.userId} value={m.userId}>
                  {m.fullName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div>
            <Label htmlFor="audit-from" className="sr-only">
              De
            </Label>
            <Input id="audit-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="De" />
          </div>
          <div>
            <Label htmlFor="audit-to" className="sr-only">
              Até
            </Label>
            <Input id="audit-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="Até" />
          </div>
          <Input
            placeholder="Buscar nos detalhes"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Buscar"
          />
          <Button type="submit" variant="outline">
            <Search className="size-4" aria-hidden /> Filtrar
          </Button>
        </form>

        {audit.isLoading ? (
          <Skeleton className="h-24 w-full" />
        ) : (audit.data?.items.length ?? 0) === 0 ? (
          <EmptyState compact icon={ScrollText} title="Nenhum registro de auditoria" />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Quando</TableHead>
                  <TableHead>Usuário</TableHead>
                  <TableHead>Ação</TableHead>
                  <TableHead>Detalhes</TableHead>
                  <TableHead>IP</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {audit.data!.items.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="whitespace-nowrap text-muted-foreground">
                      {formatDateTime(a.createdAt)}
                    </TableCell>
                    <TableCell>{a.userName ?? (a.userId ? "—" : "sistema")}</TableCell>
                    <TableCell>
                      <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{a.action}</code>
                    </TableCell>
                    <TableCell className="text-sm">{a.details ?? "—"}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{a.ipAddress ?? "—"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                {audit.data!.totalItems} registro(s) · página {audit.data!.page + 1} de{" "}
                {Math.max(1, audit.data!.totalPages)}
              </span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  Anterior
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= audit.data!.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Próxima
                </Button>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
