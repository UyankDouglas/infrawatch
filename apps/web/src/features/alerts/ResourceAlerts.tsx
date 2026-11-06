import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, BellRing } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AlertStatusBadge, SeverityBadge } from "./SeverityBadge";
import { alertsApi } from "@/services/endpoints";
import { formatRelative } from "@/lib/format";

export function ResourceAlerts({ resourceId }: { resourceId: string }) {
  const query = useQuery({
    queryKey: ["resources", "alerts", resourceId],
    queryFn: () => alertsApi.forResource(resourceId),
    refetchInterval: 15_000,
  });
  const items = query.data ?? [];
  if (items.length === 0) return null;
  return (
    <Card className="border-status-down/40">
      <CardHeader className="flex-row items-start justify-between">
        <div>
          <CardTitle className="flex items-center gap-2">
            <BellRing className="size-4 text-status-down" aria-hidden /> Alertas ativos neste recurso
          </CardTitle>
          <CardDescription>Condições em aberto ou reconhecidas.</CardDescription>
        </div>
        <Button asChild variant="ghost" size="sm">
          <Link to={`/alertas`}>
            Ver todos <ArrowRight className="size-4" />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        <ul className="divide-y divide-border">
          {items.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center gap-2 py-2">
              <SeverityBadge severity={a.severity} />
              <AlertStatusBadge status={a.status} />
              <Link to={`/alertas?alerta=${a.id}`} className="flex-1 truncate text-sm font-medium hover:underline">
                {a.title}
              </Link>
              <span className="text-xs text-muted-foreground">{formatRelative(a.triggeredAt)}</span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
