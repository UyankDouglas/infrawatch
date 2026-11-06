import { useState } from "react";
import { Link } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, CalendarX2, Plus, Wrench } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { MaintenanceStatusBadge } from "@/features/incidents/IncidentBadges";
import { MaintenanceFormDialog } from "@/features/maintenance/MaintenanceFormDialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { maintenancesApi } from "@/services/endpoints";
import { formatDateTime, formatDuration, formatRelative } from "@/lib/format";
import type { MaintenanceWindow } from "@/types/incidents";

function WindowCard({ w, onFinish }: { w: MaintenanceWindow; onFinish?: (w: MaintenanceWindow) => void }) {
  const { hasRole } = useAuth();
  const canAct = hasRole("OPERATOR") && (w.status === "SCHEDULED" || w.status === "ACTIVE");
  return (
    <li className="rounded-lg border border-border bg-card/60 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <MaintenanceStatusBadge status={w.status} />
            <h3 className="font-medium">{w.title}</h3>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {formatDateTime(w.startsAt, "dd/MM/yyyy HH:mm")} → {formatDateTime(w.endsAt, "dd/MM/yyyy HH:mm")}
            {w.durationSeconds != null ? ` · ${formatDuration(w.durationSeconds)}` : ""}
            {w.status === "SCHEDULED"
              ? ` · começa ${formatRelative(w.startsAt)}`
              : w.status === "ACTIVE"
                ? ` · termina ${formatRelative(w.endsAt)}`
                : ""}
          </p>
          {w.reason && <p className="mt-1 text-sm">{w.reason}</p>}
          <div className="mt-2 flex flex-wrap gap-1">
            {w.resources.map((r) => (
              <Badge key={r.id} variant="secondary" asChild>
                <Link to={`/recursos/${r.id}`}>{r.name}</Link>
              </Badge>
            ))}
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            {w.responsible ? `Responsável: ${w.responsible.name}` : "Sem responsável"}
            {w.createdBy ? ` · agendada por ${w.createdBy.name}` : ""}
            {w.finishReason === "ENDED_EARLY" ? " · encerrada antecipadamente" : ""}
          </p>
        </div>
        {canAct && onFinish && (
          <Button variant={w.status === "ACTIVE" ? "destructive" : "outline"} size="sm" onClick={() => onFinish(w)}>
            <CalendarX2 className="size-4" aria-hidden /> {w.status === "ACTIVE" ? "Encerrar agora" : "Cancelar"}
          </Button>
        )}
      </div>
    </li>
  );
}

export function MaintenancesPage() {
  const { hasRole } = useAuth();
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [toFinish, setToFinish] = useState<MaintenanceWindow | null>(null);

  const all = useQuery({
    queryKey: ["maintenances", "all"],
    queryFn: () => maintenancesApi.list(false),
    refetchInterval: 30_000,
  });
  const finish = useMutation({
    mutationFn: (id: string) => maintenancesApi.finish(id),
    onSuccess: (w) => {
      setToFinish(null);
      queryClient.invalidateQueries({ queryKey: ["maintenances"] });
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      toast.success(
        w.status === "CANCELLED"
          ? "Manutenção cancelada."
          : "Manutenção encerrada. Os recursos serão reavaliados na próxima verificação.",
      );
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao encerrar."),
  });

  const items = all.data ?? [];
  const current = items
    .filter((w) => w.status === "SCHEDULED" || w.status === "ACTIVE")
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const past = items.filter((w) => w.status === "COMPLETED" || w.status === "CANCELLED");

  return (
    <>
      <PageHeader
        title="Manutenções"
        description="Janelas programadas colocam recursos em “Em manutenção”: verificações continuam, mas alertas, incidentes e notificações ficam suspensos. A disponibilidade exclui esses períodos por padrão."
        actions={
          hasRole("OPERATOR") && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="size-4" aria-hidden /> Agendar manutenção
            </Button>
          )
        }
      />
      <Tabs defaultValue="current">
        <TabsList>
          <TabsTrigger value="current">Próximas e em andamento ({current.length})</TabsTrigger>
          <TabsTrigger value="past">Histórico ({past.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="current">
          <Card>
            <CardHeader>
              <CardTitle>Próximas e em andamento</CardTitle>
              <CardDescription>
                Manutenções agendadas iniciam e encerram automaticamente nos horários definidos.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {all.isLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : current.length === 0 ? (
                <EmptyState
                  compact
                  icon={CalendarClock}
                  title="Nenhuma manutenção programada"
                  description="Agende uma janela antes de intervenções planejadas para evitar alertas falsos."
                />
              ) : (
                <ul className="space-y-3">
                  {current.map((w) => (
                    <WindowCard key={w.id} w={w} onFinish={setToFinish} />
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="past">
          <Card>
            <CardHeader>
              <CardTitle>Histórico</CardTitle>
            </CardHeader>
            <CardContent>
              {all.isLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : past.length === 0 ? (
                <EmptyState compact icon={Wrench} title="Nenhuma manutenção concluída" />
              ) : (
                <ul className="space-y-3">
                  {past.map((w) => (
                    <WindowCard key={w.id} w={w} />
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <MaintenanceFormDialog open={createOpen} onOpenChange={setCreateOpen} />
      <ConfirmDialog
        open={!!toFinish}
        onOpenChange={(o) => !o && setToFinish(null)}
        title={toFinish?.status === "ACTIVE" ? "Encerrar manutenção agora" : "Cancelar manutenção"}
        description={
          toFinish?.status === "ACTIVE"
            ? "Os recursos voltam a “Aguardando verificação” e o estado real é definido pela próxima coleta."
            : "A janela agendada será cancelada e nenhum recurso será afetado."
        }
        confirmLabel={toFinish?.status === "ACTIVE" ? "Encerrar" : "Cancelar manutenção"}
        destructive={toFinish?.status === "ACTIVE"}
        pending={finish.isPending}
        onConfirm={() => toFinish && finish.mutate(toFinish.id)}
      />
    </>
  );
}
