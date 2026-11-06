import { useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock, Plus, Siren, Wrench } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { IncidentStatusBadge, MaintenanceStatusBadge } from "./IncidentBadges";
import { SeverityBadge } from "@/features/alerts/SeverityBadge";
import { IncidentFormDialog } from "./IncidentFormDialog";
import { MaintenanceFormDialog } from "@/features/maintenance/MaintenanceFormDialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { incidentsApi, maintenancesApi } from "@/services/endpoints";
import { formatDateTime, formatRelative } from "@/lib/format";

/** Incidentes ativos e manutenções próximas/em andamento de um recurso, com ações rápidas. */
export function ResourceIncidentsPanel({ resourceId }: { resourceId: string }) {
  const { hasRole } = useAuth();
  const [incidentOpen, setIncidentOpen] = useState(false);
  const [maintenanceOpen, setMaintenanceOpen] = useState(false);
  const incidents = useQuery({
    queryKey: ["incidents", "resource", resourceId],
    queryFn: () => incidentsApi.forResource(resourceId),
    refetchInterval: 15_000,
  });
  const maintenances = useQuery({
    queryKey: ["maintenances", "resource", resourceId],
    queryFn: () => maintenancesApi.forResource(resourceId),
    refetchInterval: 30_000,
  });
  const inc = incidents.data ?? [];
  const mw = maintenances.data ?? [];

  return (
    <>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-start justify-between gap-2">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Siren className="size-4" aria-hidden /> Incidentes ativos
              </CardTitle>
              <CardDescription>
                {inc.length === 0
                  ? "Nenhum incidente aberto para este recurso."
                  : `${inc.length} incidente(s) em andamento.`}
              </CardDescription>
            </div>
            {hasRole("OPERATOR") && (
              <Button size="sm" variant="outline" onClick={() => setIncidentOpen(true)}>
                <Plus className="size-4" aria-hidden /> Abrir
              </Button>
            )}
          </CardHeader>
          {inc.length > 0 && (
            <CardContent>
              <ul className="divide-y divide-border">
                {inc.map((i) => (
                  <li key={i.id} className="flex flex-wrap items-center gap-2 py-2">
                    <span className="font-mono text-xs text-muted-foreground">#{i.number}</span>
                    <SeverityBadge severity={i.severity} />
                    <IncidentStatusBadge status={i.status} />
                    <Link
                      to={`/incidentes/${i.id}`}
                      className="min-w-0 flex-1 truncate text-sm font-medium hover:underline"
                    >
                      {i.title}
                    </Link>
                    <span className="text-xs text-muted-foreground">{formatRelative(i.openedAt)}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          )}
        </Card>
        <Card>
          <CardHeader className="flex-row items-start justify-between gap-2">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Wrench className="size-4" aria-hidden /> Manutenções
              </CardTitle>
              <CardDescription>
                {mw.length === 0 ? "Nenhuma janela programada para este recurso." : "Próximas e em andamento."}
              </CardDescription>
            </div>
            {hasRole("OPERATOR") && (
              <Button size="sm" variant="outline" onClick={() => setMaintenanceOpen(true)}>
                <CalendarClock className="size-4" aria-hidden /> Agendar
              </Button>
            )}
          </CardHeader>
          {mw.length > 0 && (
            <CardContent>
              <ul className="divide-y divide-border">
                {mw.map((w) => (
                  <li key={w.id} className="flex flex-wrap items-center gap-2 py-2">
                    <MaintenanceStatusBadge status={w.status} />
                    <Link to="/manutencoes" className="min-w-0 flex-1 truncate text-sm font-medium hover:underline">
                      {w.title}
                    </Link>
                    <span className="text-xs text-muted-foreground">
                      {formatDateTime(w.startsAt, "dd/MM HH:mm")} → {formatDateTime(w.endsAt, "dd/MM HH:mm")}
                    </span>
                  </li>
                ))}
              </ul>
            </CardContent>
          )}
        </Card>
      </div>
      <IncidentFormDialog open={incidentOpen} onOpenChange={setIncidentOpen} defaultResourceId={resourceId} />
      <MaintenanceFormDialog
        open={maintenanceOpen}
        onOpenChange={setMaintenanceOpen}
        defaultResourceIds={[resourceId]}
      />
    </>
  );
}
