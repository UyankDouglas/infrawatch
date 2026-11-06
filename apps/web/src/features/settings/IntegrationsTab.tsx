import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Loader2, Mail, Pencil, Plus, Send, Trash2, Webhook } from "lucide-react";
import { toast } from "sonner";
import { EmptyState } from "@/components/EmptyState";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { SeverityBadge } from "@/features/alerts/SeverityBadge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiError } from "@/services/api";
import { integrationsApi } from "@/services/endpoints";
import { formatDateTime } from "@/lib/format";
import type { ChannelInput, DeliveryStatus, NotificationChannel } from "@/types/reports";

const schema = z.object({
  name: z.string().trim().min(1, "Informe o nome.").max(120),
  type: z.enum(["EMAIL", "WEBHOOK"]),
  enabled: z.boolean(),
  emailRecipients: z.string(),
  webhookUrl: z.string().trim(),
  webhookSecret: z.string(),
  minSeverity: z.enum(["INFO", "WARNING", "HIGH", "CRITICAL"]),
  eventTypes: z.array(z.string()),
});
type FormValues = z.infer<typeof schema>;

const SEVERITIES = [
  { value: "INFO", label: "Informativo" },
  { value: "WARNING", label: "Atenção" },
  { value: "HIGH", label: "Alta" },
  { value: "CRITICAL", label: "Crítica" },
] as const;

const DELIVERY_STATUS: Record<
  DeliveryStatus,
  { label: string; variant: "default" | "secondary" | "outline" | "destructive" }
> = {
  PENDING: { label: "Pendente", variant: "outline" },
  SENT: { label: "Enviada", variant: "default" },
  FAILED: { label: "Falhou", variant: "destructive" },
  SKIPPED: { label: "Ignorada", variant: "secondary" },
  EXPIRED: { label: "Expirada", variant: "secondary" },
};

function ChannelDialog({
  open,
  onOpenChange,
  editing,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editing: NotificationChannel | null;
}) {
  const queryClient = useQueryClient();
  const options = useQuery({ queryKey: ["integrations", "options"], queryFn: integrationsApi.options, enabled: open });
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      type: "WEBHOOK",
      enabled: true,
      emailRecipients: "",
      webhookUrl: "",
      webhookSecret: "",
      minSeverity: "WARNING",
      eventTypes: [],
    },
  });

  useEffect(() => {
    if (!open) return;
    form.reset(
      editing
        ? {
            name: editing.name,
            type: editing.type,
            enabled: editing.enabled,
            emailRecipients: editing.emailRecipients.join(", "),
            webhookUrl: editing.webhookUrl ?? "",
            webhookSecret: "",
            minSeverity: editing.minSeverity,
            eventTypes: editing.eventTypes,
          }
        : {
            name: "",
            type: "WEBHOOK",
            enabled: true,
            emailRecipients: "",
            webhookUrl: "",
            webhookSecret: "",
            minSeverity: "WARNING",
            eventTypes: [],
          },
    );
  }, [open, editing, form]);

  const type = form.watch("type");

  const save = useMutation({
    mutationFn: (values: FormValues) => {
      const data: ChannelInput = {
        name: values.name,
        type: values.type,
        enabled: values.enabled,
        minSeverity: values.minSeverity,
        eventTypes: values.eventTypes,
      };
      if (values.type === "EMAIL") {
        data.emailRecipients = values.emailRecipients
          .split(/[,;\s]+/)
          .map((s) => s.trim())
          .filter(Boolean);
      } else {
        data.webhookUrl = values.webhookUrl;
        if (values.webhookSecret) data.webhookSecret = values.webhookSecret;
      }
      return editing ? integrationsApi.update(editing.id, data) : integrationsApi.create(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["integrations"] });
      toast.success(editing ? "Canal atualizado." : "Canal criado.");
      onOpenChange(false);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.errors) {
        Object.entries(e.errors).forEach(([field, message]) => form.setError(field as keyof FormValues, { message }));
      }
      toast.error(e instanceof ApiError ? e.message : "Falha ao salvar canal.");
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{editing ? "Editar canal" : "Novo canal de notificação"}</DialogTitle>
          <DialogDescription>
            Webhooks só podem apontar para destinos permitidos pela política de alvos (allowlist e proteção anti-SSRF),
            a mesma usada nas verificações.
          </DialogDescription>
        </DialogHeader>
        <form id="channel-form" className="space-y-4" onSubmit={form.handleSubmit((v) => save.mutate(v))} noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="ch-name">Nome</Label>
              <Input id="ch-name" {...form.register("name")} aria-invalid={!!form.formState.errors.name} />
              {form.formState.errors.name && (
                <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label>Tipo</Label>
              <Controller
                control={form.control}
                name="type"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange} disabled={!!editing}>
                    <SelectTrigger aria-label="Tipo do canal">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="WEBHOOK">Webhook (HTTP POST JSON)</SelectItem>
                      <SelectItem value="EMAIL">E-mail (SMTP)</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>
          {type === "EMAIL" ? (
            <div className="space-y-2">
              <Label htmlFor="ch-recipients">Destinatários (separe por vírgula)</Label>
              <Input
                id="ch-recipients"
                placeholder="noc@empresa.com, ti@empresa.com"
                {...form.register("emailRecipients")}
              />
            </div>
          ) : (
            <>
              <div className="space-y-2">
                <Label htmlFor="ch-url">URL do webhook</Label>
                <Input
                  id="ch-url"
                  placeholder="http://host.docker.internal:9101/__webhook"
                  {...form.register("webhookUrl")}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ch-secret">Segredo HMAC (opcional)</Label>
                <Input
                  id="ch-secret"
                  type="password"
                  autoComplete="off"
                  placeholder={
                    editing?.hasSecret ? "•••••• (mantido se vazio)" : "Usado no cabeçalho X-InfraWatch-Signature"
                  }
                  {...form.register("webhookSecret")}
                />
              </div>
            </>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label>Severidade mínima</Label>
              <Controller
                control={form.control}
                name="minSeverity"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Severidade mínima">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {SEVERITIES.map((s) => (
                        <SelectItem key={s.value} value={s.value}>
                          {s.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="flex items-center gap-3 pt-6">
              <Controller
                control={form.control}
                name="enabled"
                render={({ field }) => (
                  <Switch id="ch-enabled" checked={field.value} onCheckedChange={field.onChange} />
                )}
              />
              <Label htmlFor="ch-enabled">Canal ativo</Label>
            </div>
          </div>
          <div className="space-y-2">
            <Label>Tipos de evento (vazio = todos)</Label>
            <Controller
              control={form.control}
              name="eventTypes"
              render={({ field }) => (
                <div className="grid gap-2 sm:grid-cols-2">
                  {(options.data?.eventTypes ?? []).map((t) => {
                    const checked = field.value.includes(t.value);
                    return (
                      <label key={t.value} className="flex items-center gap-2 text-sm">
                        <Checkbox
                          checked={checked}
                          onCheckedChange={(v) =>
                            field.onChange(v ? [...field.value, t.value] : field.value.filter((x) => x !== t.value))
                          }
                        />
                        {t.label}
                      </label>
                    );
                  })}
                </div>
              )}
            />
          </div>
        </form>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button type="submit" form="channel-form" disabled={save.isPending}>
            {save.isPending && <Loader2 className="size-4 animate-spin" aria-hidden />}
            {editing ? "Salvar" : "Criar canal"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function IntegrationsTab() {
  const queryClient = useQueryClient();
  const channels = useQuery({ queryKey: ["integrations", "channels"], queryFn: integrationsApi.channels });
  const [filterChannel, setFilterChannel] = useState<string>("__all__");
  const deliveries = useQuery({
    queryKey: ["integrations", "deliveries", filterChannel],
    queryFn: () => integrationsApi.deliveries(filterChannel === "__all__" ? undefined : filterChannel, 0, 25),
  });
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<NotificationChannel | null>(null);
  const [toDelete, setToDelete] = useState<NotificationChannel | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["integrations"] });
  const test = useMutation({
    mutationFn: (id: string) => integrationsApi.test(id),
    onSuccess: (d) => {
      invalidate();
      if (d.status === "SENT") toast.success("Mensagem de teste enviada.");
      else toast.warning(`Teste registrado como "${DELIVERY_STATUS[d.status].label}": ${d.lastError ?? ""}`);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao testar canal."),
  });
  const remove = useMutation({
    mutationFn: (id: string) => integrationsApi.remove(id),
    onSuccess: () => {
      setToDelete(null);
      invalidate();
      toast.success("Canal removido.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao remover canal."),
  });
  const toggle = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => integrationsApi.update(id, { enabled }),
    onSuccess: invalidate,
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao atualizar canal."),
  });

  const dispatchEnabled = channels.data?.[0]?.dispatchEnabled;

  return (
    <div className="space-y-4">
      {channels.data && channels.data.length > 0 && dispatchEnabled === false && (
        <Alert>
          <AlertTriangle className="size-4" aria-hidden />
          <AlertDescription>
            O envio externo está desativado neste ambiente (<code>INFRAWATCH_INTEGRATIONS_ENABLED=false</code>). As
            entregas são registradas como "Ignoradas" e nada sai da máquina — comportamento padrão em desenvolvimento.
          </AlertDescription>
        </Alert>
      )}
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div>
            <CardTitle>Canais externos</CardTitle>
            <CardDescription>
              E-mail (SMTP) e webhooks assinados (HMAC-SHA256). As notificações internas continuam sempre ativas.
            </CardDescription>
          </div>
          <Button
            onClick={() => {
              setEditing(null);
              setDialogOpen(true);
            }}
          >
            <Plus className="size-4" aria-hidden /> Novo canal
          </Button>
        </CardHeader>
        <CardContent>
          {channels.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : (channels.data?.length ?? 0) === 0 ? (
            <EmptyState
              compact
              icon={Webhook}
              title="Nenhum canal configurado"
              description="Crie um webhook ou um canal de e-mail para receber alertas e incidentes fora do painel."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Canal</TableHead>
                  <TableHead>Destino</TableHead>
                  <TableHead>Severidade mín.</TableHead>
                  <TableHead>Eventos</TableHead>
                  <TableHead>Última entrega</TableHead>
                  <TableHead>Ativo</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {channels.data!.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell>
                      <div className="flex items-center gap-2 font-medium">
                        {c.type === "EMAIL" ? (
                          <Mail className="size-4 text-muted-foreground" aria-hidden />
                        ) : (
                          <Webhook className="size-4 text-muted-foreground" aria-hidden />
                        )}
                        {c.name}
                      </div>
                      {c.lastError && <p className="text-xs text-destructive">{c.lastError}</p>}
                    </TableCell>
                    <TableCell className="max-w-[260px] truncate font-mono text-xs text-muted-foreground">
                      {c.type === "EMAIL" ? c.emailRecipients.join(", ") : c.webhookUrl}
                      {c.type === "WEBHOOK" && c.hasSecret && (
                        <Badge variant="outline" className="ml-2">
                          assinado
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <SeverityBadge severity={c.minSeverity} label={c.minSeverityLabel} />
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {c.eventTypes.length === 0 ? "todos" : c.eventTypes.join(", ")}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                      {c.lastDeliveryAt ? formatDateTime(c.lastDeliveryAt) : "—"}
                    </TableCell>
                    <TableCell>
                      <Switch
                        checked={c.enabled}
                        aria-label={`Ativar canal ${c.name}`}
                        onCheckedChange={(enabled) => toggle.mutate({ id: c.id, enabled })}
                      />
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-right">
                      <Button variant="ghost" size="sm" onClick={() => test.mutate(c.id)} disabled={test.isPending}>
                        <Send className="size-4" aria-hidden /> Testar
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Editar canal ${c.name}`}
                        onClick={() => {
                          setEditing(c);
                          setDialogOpen(true);
                        }}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Remover canal ${c.name}`}
                        onClick={() => setToDelete(c)}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div>
            <CardTitle>Entregas externas</CardTitle>
            <CardDescription>
              Fila (outbox) com tentativas e erros. Entregas falhas são reagendadas com espera progressiva (30 s, 60 s,
              2 min…) até 5 tentativas.
            </CardDescription>
          </div>
          <Select value={filterChannel} onValueChange={setFilterChannel}>
            <SelectTrigger className="w-[200px]" aria-label="Filtrar por canal">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">Todos os canais</SelectItem>
              {channels.data?.map((c) => (
                <SelectItem key={c.id} value={c.id}>
                  {c.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent className="p-0">
          {deliveries.isLoading ? (
            <Skeleton className="m-6 h-20" />
          ) : (deliveries.data?.items.length ?? 0) === 0 ? (
            <EmptyState compact icon={Send} title="Nenhuma entrega registrada" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Quando</TableHead>
                  <TableHead>Canal</TableHead>
                  <TableHead>Evento</TableHead>
                  <TableHead>Título</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Tentativas</TableHead>
                  <TableHead>Detalhe</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {deliveries.data!.items.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                      {formatDateTime(d.createdAt)}
                    </TableCell>
                    <TableCell>{d.channelName}</TableCell>
                    <TableCell>
                      <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{d.eventType}</code>
                    </TableCell>
                    <TableCell className="max-w-[260px] truncate">{d.title}</TableCell>
                    <TableCell>
                      <Badge variant={DELIVERY_STATUS[d.status].variant}>{DELIVERY_STATUS[d.status].label}</Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{d.attempts}</TableCell>
                    <TableCell className="max-w-[280px] truncate text-xs text-muted-foreground">
                      {d.responseCode ? `HTTP ${d.responseCode}` : ""}
                      {d.lastError ? ` ${d.lastError}` : ""}
                      {d.status === "PENDING" && d.nextAttemptAt
                        ? `próxima: ${formatDateTime(d.nextAttemptAt, "HH:mm:ss")}`
                        : ""}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <ChannelDialog open={dialogOpen} onOpenChange={setDialogOpen} editing={editing} />
      <ConfirmDialog
        open={!!toDelete}
        onOpenChange={(open) => !open && setToDelete(null)}
        title="Remover canal"
        description={`O canal "${toDelete?.name}" e seu histórico de entregas serão removidos.`}
        confirmLabel="Remover"
        destructive
        pending={remove.isPending}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
      />
    </div>
  );
}
