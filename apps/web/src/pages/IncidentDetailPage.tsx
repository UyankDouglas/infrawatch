import { useState } from "react";
import { Link, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, CircleX, MessageSquare, Pencil, Save, Send, UserRound } from "lucide-react";
import { toast } from "sonner";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { KpiCard } from "@/features/dashboard/KpiCard";
import { SeverityBadge } from "@/features/alerts/SeverityBadge";
import { INCIDENT_STATUS_META, IncidentStatusBadge } from "@/features/incidents/IncidentBadges";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { incidentsApi, organizationApi } from "@/services/endpoints";
import { formatDateTime, formatDuration, formatRelative } from "@/lib/format";
import type { IncidentStatus, TimelineItem } from "@/types/incidents";
import { Clock, Timer, Activity } from "lucide-react";

const NONE = "__none__";

const EVENT_LABEL: Record<string, string> = {
  CREATED: "Incidente aberto",
  STATUS_CHANGED: "Status alterado",
  ASSIGNED: "Responsável atribuído",
  UNASSIGNED: "Responsável removido",
  SEVERITY_CHANGED: "Severidade alterada",
  CAUSE_SET: "Causa informada",
  NOTES_UPDATED: "Observações atualizadas",
  TITLE_UPDATED: "Título alterado",
  DESCRIPTION_UPDATED: "Descrição alterada",
  COMMENTED: "Comentário",
  RESOURCE_RECOVERED: "Transição do recurso",
  RESOURCE_STATUS: "Estado do recurso",
  AUTO_RESOLVED: "Resolvido automaticamente",
  LINKED_ALERT: "Alerta vinculado",
};

function statusLabel(v: string | null): string {
  if (!v) return "";
  return (INCIDENT_STATUS_META as Record<string, { label: string }>)[v]?.label ?? v;
}

function TimelineEntry({ item }: { item: TimelineItem }) {
  const dot =
    item.kind === "comment"
      ? "bg-primary"
      : item.kind === "resource"
        ? "bg-status-maintenance"
        : item.eventType === "CREATED"
          ? "bg-status-down"
          : item.eventType.includes("RESOLVED")
            ? "bg-status-up"
            : "bg-muted-foreground";
  return (
    <li className="relative pb-5 pl-6 last:pb-0">
      <span className={`absolute top-1.5 left-0 status-dot ${dot}`} aria-hidden />
      <div className="flex flex-wrap items-baseline gap-x-2 text-sm">
        <span className="font-medium">{EVENT_LABEL[item.eventType] ?? item.eventType}</span>
        {item.user && <span className="text-muted-foreground">por {item.user.name}</span>}
        {!item.user && item.kind !== "comment" && <span className="text-muted-foreground">pelo sistema</span>}
        <span className="text-xs text-muted-foreground" title={item.at}>
          {formatDateTime(item.at)}
        </span>
      </div>
      {(item.fromValue || item.toValue) && item.eventType !== "LINKED_ALERT" && (
        <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
          {item.fromValue && (
            <span>
              {item.eventType === "STATUS_CHANGED" || item.eventType === "AUTO_RESOLVED"
                ? statusLabel(item.fromValue)
                : item.fromValue}
            </span>
          )}
          {item.fromValue && item.toValue && <ArrowRight className="size-3" aria-hidden />}
          {item.toValue && (
            <span className="font-medium text-foreground">
              {item.eventType === "STATUS_CHANGED" || item.eventType === "AUTO_RESOLVED" || item.eventType === "CREATED"
                ? statusLabel(item.toValue)
                : item.toValue}
            </span>
          )}
        </p>
      )}
      {item.text && (
        <p
          className={
            item.kind === "comment"
              ? "mt-1 rounded-md bg-muted/60 px-3 py-2 text-sm whitespace-pre-wrap"
              : "mt-0.5 text-sm text-muted-foreground"
          }
        >
          {item.text}
        </p>
      )}
    </li>
  );
}

export function IncidentDetailPage() {
  const { id = "" } = useParams();
  const { hasRole } = useAuth();
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");
  const [comment, setComment] = useState("");
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ title: "", description: "", cause: "", notes: "" });

  const detail = useQuery({
    queryKey: ["incidents", "detail", id],
    queryFn: () => incidentsApi.get(id),
    refetchInterval: 15_000,
  });
  const members = useQuery({ queryKey: ["members"], queryFn: organizationApi.members });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["incidents"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };
  const changeStatus = useMutation({
    mutationFn: (status: IncidentStatus) => incidentsApi.changeStatus(id, status, note),
    onSuccess: (i) => {
      invalidate();
      setNote("");
      toast.success(`Incidente #${i.number}: ${i.statusLabel}.`);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao alterar status."),
  });
  const addComment = useMutation({
    mutationFn: () => incidentsApi.comment(id, comment),
    onSuccess: () => {
      invalidate();
      setComment("");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao comentar."),
  });
  const patch = useMutation({
    mutationFn: (data: Parameters<typeof incidentsApi.patch>[1]) => incidentsApi.patch(id, data),
    onSuccess: () => {
      invalidate();
      setEditing(false);
      toast.success("Incidente atualizado.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao atualizar."),
  });

  if (detail.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-72" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }
  if (detail.isError || !detail.data) {
    return (
      <EmptyState
        icon={CircleX}
        title="Incidente não encontrado"
        action={
          <Button asChild variant="outline">
            <Link to="/incidentes">Voltar para incidentes</Link>
          </Button>
        }
      />
    );
  }

  const { incident: i, timeline, allowedTransitions } = detail.data;
  const canOperate = hasRole("OPERATOR");

  return (
    <>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-2">
          <Link
            to="/incidentes"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="size-4" aria-hidden /> Incidentes
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <span className="font-mono text-sm text-muted-foreground">#{i.number}</span>
            <h1 className="text-2xl font-semibold tracking-tight">{i.title}</h1>
            <SeverityBadge severity={i.severity} />
            <IncidentStatusBadge status={i.status} />
            <Badge variant="outline">{i.originLabel}</Badge>
          </div>
          {i.description && (
            <p className="max-w-3xl text-sm text-muted-foreground whitespace-pre-wrap">{i.description}</p>
          )}
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            {i.resourceId && (
              <span>
                Recurso:{" "}
                <Link to={`/recursos/${i.resourceId}`} className="font-medium text-foreground hover:underline">
                  {i.resourceName}
                </Link>
              </span>
            )}
            {i.alertEventId && (
              <Link to={`/alertas?estado=todos&alerta=${i.alertEventId}`} className="hover:underline">
                Ver alerta vinculado
              </Link>
            )}
            {i.createdBy && <span>Aberto por {i.createdBy.name}</span>}
          </div>
        </div>
        {canOperate && (
          <Button
            variant="outline"
            onClick={() => {
              setDraft({
                title: i.title,
                description: i.description ?? "",
                cause: i.cause ?? "",
                notes: i.notes ?? "",
              });
              setEditing((e) => !e);
            }}
          >
            <Pencil className="size-4" aria-hidden /> {editing ? "Fechar edição" : "Editar"}
          </Button>
        )}
      </div>

      <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <KpiCard
          title="Aberto em"
          value={formatDateTime(i.openedAt, "dd/MM HH:mm")}
          icon={Clock}
          tone="down"
          hint={formatRelative(i.openedAt)}
        />
        <KpiCard
          title="Duração"
          value={i.durationSeconds != null ? formatDuration(i.durationSeconds) : null}
          icon={Timer}
          tone={i.resolvedAt ? "up" : "degraded"}
          hint={i.resolvedAt ? `Resolvido ${formatDateTime(i.resolvedAt, "dd/MM HH:mm")}` : "em andamento"}
        />
        <KpiCard
          title="Reconhecido"
          value={i.acknowledgedAt ? formatRelative(i.acknowledgedAt) : null}
          icon={Activity}
          tone="default"
          emptyLabel="Ainda não"
        />
        <KpiCard
          title="Responsável"
          value={i.assignee?.name ?? null}
          icon={UserRound}
          tone="info"
          emptyLabel="Não atribuído"
        />
      </section>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {editing && (
            <Card>
              <CardHeader>
                <CardTitle>Editar incidente</CardTitle>
                <CardDescription>Cada alteração entra no histórico.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-1">
                  <Label htmlFor="ed-title">Título</Label>
                  <Input
                    id="ed-title"
                    value={draft.title}
                    onChange={(e) => setDraft({ ...draft, title: e.target.value })}
                    maxLength={200}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="ed-desc">Descrição</Label>
                  <Textarea
                    id="ed-desc"
                    rows={3}
                    value={draft.description}
                    onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="ed-cause">Causa informada</Label>
                  <Textarea
                    id="ed-cause"
                    rows={2}
                    value={draft.cause}
                    onChange={(e) => setDraft({ ...draft, cause: e.target.value })}
                    maxLength={2000}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="ed-notes">Observações</Label>
                  <Textarea
                    id="ed-notes"
                    rows={2}
                    value={draft.notes}
                    onChange={(e) => setDraft({ ...draft, notes: e.target.value })}
                    maxLength={4000}
                  />
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-1">
                    <Label>Severidade</Label>
                    <Select
                      value={i.severity}
                      onValueChange={(v) => patch.mutate({ severity: v as typeof i.severity })}
                    >
                      <SelectTrigger aria-label="Severidade">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="CRITICAL">Crítico</SelectItem>
                        <SelectItem value="HIGH">Alto</SelectItem>
                        <SelectItem value="WARNING">Atenção</SelectItem>
                        <SelectItem value="INFO">Informativo</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <Label>Responsável</Label>
                    <Select
                      value={i.assignee?.id ?? NONE}
                      onValueChange={(v) => patch.mutate(v === NONE ? { clearAssignee: true } : { assigneeId: v })}
                    >
                      <SelectTrigger aria-label="Responsável">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>Sem responsável</SelectItem>
                        {(members.data ?? []).map((m) => (
                          <SelectItem key={m.userId} value={m.userId}>
                            {m.fullName} · {m.roleLabel}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <Button
                  onClick={() =>
                    patch.mutate({
                      title: draft.title,
                      description: draft.description,
                      cause: draft.cause,
                      notes: draft.notes,
                    })
                  }
                  disabled={patch.isPending || !draft.title.trim()}
                >
                  <Save className="size-4" aria-hidden /> Salvar
                </Button>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader>
              <CardTitle>Timeline</CardTitle>
              <CardDescription>
                Histórico de alterações, comentários e transições do recurso durante o incidente.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ol className="relative border-l border-border">
                {timeline.map((t: TimelineItem, idx: number) => (
                  <TimelineEntry key={idx} item={t} />
                ))}
              </ol>
              {canOperate && (
                <form
                  className="mt-5 flex gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (comment.trim()) addComment.mutate();
                  }}
                >
                  <Textarea
                    placeholder="Adicionar comentário…"
                    rows={2}
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    maxLength={4000}
                    aria-label="Comentário"
                  />
                  <Button
                    type="submit"
                    disabled={!comment.trim() || addComment.isPending}
                    aria-label="Enviar comentário"
                  >
                    <Send className="size-4" />
                  </Button>
                </form>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Ações</CardTitle>
              <CardDescription>Transições permitidas a partir de {i.statusLabel.toLowerCase()}.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {!canOperate ? (
                <p className="text-sm text-muted-foreground">
                  Somente operadores e administradores alteram incidentes.
                </p>
              ) : allowedTransitions.length === 0 ? (
                <p className="text-sm text-muted-foreground">Incidente encerrado. Nenhuma ação disponível.</p>
              ) : (
                <>
                  <Textarea
                    placeholder="Observação (opcional)"
                    rows={2}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    maxLength={1000}
                  />
                  <div className="flex flex-col gap-2">
                    {allowedTransitions.map((t: IncidentStatus) => (
                      <Button
                        key={t}
                        variant={t === "RESOLVED" ? "default" : "outline"}
                        onClick={() => changeStatus.mutate(t)}
                        disabled={changeStatus.isPending}
                      >
                        {t === "ACKNOWLEDGED"
                          ? "Reconhecer"
                          : t === "INVESTIGATING"
                            ? i.status === "RESOLVED"
                              ? "Reabrir (investigar)"
                              : "Iniciar investigação"
                            : t === "RESOLVED"
                              ? "Resolver"
                              : "Encerrar"}
                      </Button>
                    ))}
                  </div>
                </>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Detalhes</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="text-muted-foreground">Causa informada</dt>
                  <dd className="whitespace-pre-wrap">{i.cause ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Observações</dt>
                  <dd className="whitespace-pre-wrap">{i.notes ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Comentários</dt>
                  <dd className="flex items-center gap-1">
                    <MessageSquare className="size-3.5" aria-hidden /> {i.commentCount}
                  </dd>
                </div>
                {i.closedAt && (
                  <div>
                    <dt className="text-muted-foreground">Encerrado em</dt>
                    <dd>{formatDateTime(i.closedAt)}</dd>
                  </div>
                )}
              </dl>
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}
