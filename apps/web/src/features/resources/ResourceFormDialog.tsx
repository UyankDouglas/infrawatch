import { useEffect } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
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
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { ApiError } from "@/services/api";
import { groupsApi, resourcesApi } from "@/services/endpoints";
import { formatInterval } from "@/lib/format";
import type { Resource, ResourceInput } from "@/types/api";

const NONE = "__none__";

const schema = z.object({
  name: z.string().trim().min(1, "Informe o nome.").max(120),
  description: z.string().max(1000).optional(),
  type: z.enum(["HTTP", "HTTPS", "HEALTH_ENDPOINT", "AGENT_SERVER"]),
  targetUrl: z.string().trim().min(1, "Informe a URL ou o identificador do servidor.").max(2048),
  environment: z.enum(["DEVELOPMENT", "STAGING", "PRODUCTION"]),
  groupId: z.string().optional(),
  owner: z.string().max(120).optional(),
  tags: z.string().optional(),
  intervalSeconds: z.number().int().min(30, "Mínimo de 30 segundos.").max(86400),
  timeoutMs: z
    .number({ error: "Informe o timeout." })
    .int()
    .min(100, "Mínimo de 100 ms.")
    .max(30000, "Máximo de 30000 ms."),
  httpMethod: z.enum(["GET", "HEAD"]),
  expectedStatusCodes: z
    .string()
    .trim()
    .regex(/^\d{3}(\s*-\s*\d{3})?(\s*,\s*\d{3}(\s*-\s*\d{3})?)*$/, "Use códigos ou faixas, ex.: 200-299,301"),
  degradedLatencyMs: z.number({ error: "Informe um número." }).int().min(1).max(60000).nullable(),
  failureThreshold: z.number({ error: "Informe um número." }).int().min(1).max(20),
  recoveryThreshold: z.number({ error: "Informe um número." }).int().min(1).max(20),
  followRedirects: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

const DEFAULTS: FormValues = {
  name: "",
  description: "",
  type: "HEALTH_ENDPOINT",
  targetUrl: "",
  environment: "PRODUCTION",
  groupId: NONE,
  owner: "",
  tags: "",
  intervalSeconds: 60,
  timeoutMs: 5000,
  httpMethod: "GET",
  expectedStatusCodes: "200-299",
  degradedLatencyMs: null,
  failureThreshold: 3,
  recoveryThreshold: 2,
  followRedirects: false,
};

function toValues(r: Resource): FormValues {
  return {
    name: r.name,
    description: r.description ?? "",
    type: r.type,
    targetUrl: r.targetUrl,
    environment: r.environment,
    groupId: r.group?.id ?? NONE,
    owner: r.owner ?? "",
    tags: r.tags.join(", "),
    intervalSeconds: r.check?.intervalSeconds ?? 60,
    timeoutMs: r.check?.timeoutMs ?? 5000,
    httpMethod: (r.check?.httpMethod as "GET" | "HEAD") ?? "GET",
    expectedStatusCodes: r.check?.expectedStatusCodes ?? "200-299",
    degradedLatencyMs: r.check?.degradedLatencyMs ?? null,
    failureThreshold: r.check?.failureThreshold ?? 3,
    recoveryThreshold: r.check?.recoveryThreshold ?? 2,
    followRedirects: r.check?.followRedirects ?? false,
  };
}

function toInput(v: FormValues, editing: boolean): ResourceInput {
  return {
    name: v.name,
    description: v.description || null,
    type: v.type,
    targetUrl: v.targetUrl,
    environment: v.environment,
    groupId: v.groupId && v.groupId !== NONE ? v.groupId : null,
    clearGroup: editing && (!v.groupId || v.groupId === NONE) ? true : undefined,
    owner: v.owner || null,
    tags: (v.tags ?? "")
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean)
      .slice(0, 10),
    check: {
      intervalSeconds: v.intervalSeconds,
      timeoutMs: v.timeoutMs,
      httpMethod: v.httpMethod,
      expectedStatusCodes: v.expectedStatusCodes.replace(/\s/g, ""),
      degradedLatencyMs: v.degradedLatencyMs ?? null,
      failureThreshold: v.failureThreshold,
      recoveryThreshold: v.recoveryThreshold,
      followRedirects: v.followRedirects,
    },
  };
}

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  resource?: Resource | null;
  onSaved?: (resource: Resource) => void;
}

export function ResourceFormDialog({ open, onOpenChange, resource, onSaved }: Props) {
  const queryClient = useQueryClient();
  const editing = !!resource;
  const options = useQuery({
    queryKey: ["resources", "options"],
    queryFn: resourcesApi.options,
    staleTime: Infinity,
    enabled: open,
  });
  const groups = useQuery({ queryKey: ["groups"], queryFn: groupsApi.list, enabled: open });

  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: DEFAULTS });

  useEffect(() => {
    if (open) form.reset(resource ? toValues(resource) : DEFAULTS);
  }, [open, resource, form]);

  const mutation = useMutation({
    mutationFn: (values: FormValues) => {
      const input = toInput(values, editing);
      return editing ? resourcesApi.update(resource!.id, input) : resourcesApi.create(input);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      toast.success(
        editing ? "Recurso atualizado." : "Recurso cadastrado. A primeira verificação será executada em instantes.",
      );
      onOpenChange(false);
      onSaved?.(saved);
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        if (error.errors) {
          Object.entries(error.errors).forEach(([field, message]) => {
            const key = field.replace(/^check\./, "") as keyof FormValues;
            form.setError(key, { message });
          });
        }
        if (error.code.startsWith("target_") || error.code === "dns_failure") {
          form.setError("targetUrl", { message: error.message });
        } else if (error.code === "interval_too_short") {
          form.setError("intervalSeconds", { message: error.message });
        } else if (error.code === "timeout_vs_interval") {
          form.setError("timeoutMs", { message: error.message });
        } else if (error.status === 409) {
          form.setError("name", { message: error.message });
        } else if (!error.errors) {
          toast.error(error.message);
        }
      } else {
        toast.error("Falha ao salvar o recurso.");
      }
    },
  });

  const err = (name: keyof FormValues) => form.formState.errors[name]?.message as string | undefined;
  const typeOptions = options.data?.types ?? [];
  const selectedType = form.watch("type");
  const httpCheck = typeOptions.find((t) => t.value === selectedType)?.httpCheck ?? true;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{editing ? "Editar recurso" : "Cadastrar recurso monitorado"}</DialogTitle>
          <DialogDescription>
            Somente destinos autorizados pela política do servidor são aceitos. Verificações são executadas pelo
            backend.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-5" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="name">Nome</Label>
              <Input
                id="name"
                {...form.register("name")}
                aria-invalid={!!err("name")}
                placeholder="Ex.: API de Pagamentos"
              />
              {err("name") && <p className="text-xs text-destructive">{err("name")}</p>}
            </div>
            <div className="space-y-2">
              <Label>Tipo</Label>
              <Controller
                control={form.control}
                name="type"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Tipo">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {(typeOptions.length
                        ? typeOptions
                        : [{ value: "HEALTH_ENDPOINT", label: "Aplicação com health check", httpCheck: true }]
                      ).map((t) => (
                        <SelectItem key={t.value} value={t.value}>
                          {t.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-2">
              <Label>Ambiente</Label>
              <Controller
                control={form.control}
                name="environment"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Ambiente">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {(
                        options.data?.environments ?? [
                          { value: "DEVELOPMENT", label: "Desenvolvimento" },
                          { value: "STAGING", label: "Homologação" },
                          { value: "PRODUCTION", label: "Produção" },
                        ]
                      ).map((e) => (
                        <SelectItem key={e.value} value={e.value}>
                          {e.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="targetUrl">{httpCheck ? "URL autorizada" : "Identificação do servidor"}</Label>
              <Input
                id="targetUrl"
                {...form.register("targetUrl")}
                aria-invalid={!!err("targetUrl")}
                placeholder={httpCheck ? "http://demo-healthy:9101/health" : "srv-app-01.interno"}
                inputMode={httpCheck ? "url" : "text"}
              />
              {err("targetUrl") && <p className="text-xs text-destructive">{err("targetUrl")}</p>}
              <p className="text-xs text-muted-foreground">
                {httpCheck
                  ? "Na demonstração local: localhost:9101 (saudável), 9102 (degradada), 9103 (instável)."
                  : "Servidores com agente não são sondados pela rede: informe apenas o nome/FQDN/IP do host. As métricas chegam pelo agente autorizado, registrado após o cadastro."}
              </p>
            </div>
            <div className="space-y-2">
              <Label>Grupo</Label>
              <Controller
                control={form.control}
                name="groupId"
                render={({ field }) => (
                  <Select value={field.value ?? NONE} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Grupo">
                      <SelectValue placeholder="Sem grupo" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE}>Sem grupo</SelectItem>
                      {(groups.data ?? []).map((g) => (
                        <SelectItem key={g.id} value={g.id}>
                          {g.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="owner">Responsável</Label>
              <Input id="owner" {...form.register("owner")} placeholder="Equipe ou pessoa" />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="tags">Tags (separadas por vírgula)</Label>
              <Input id="tags" {...form.register("tags")} placeholder="pagamentos, critico" />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="description">Descrição</Label>
              <Textarea id="description" rows={2} {...form.register("description")} />
            </div>
          </div>

          <Separator />
          <div>
            <h3 className="text-sm font-semibold">Verificação HTTP</h3>
            <p className="text-xs text-muted-foreground">
              {httpCheck
                ? "Intervalo mínimo de 30 s. O timeout precisa ser menor que o intervalo."
                : "Não se aplica a servidores com agente: a disponibilidade é derivada das amostras recebidas."}
            </p>
          </div>
          <fieldset disabled={!httpCheck} className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-2">
              <Label>Intervalo</Label>
              <Controller
                control={form.control}
                name="intervalSeconds"
                render={({ field }) => (
                  <Select value={String(field.value)} onValueChange={(v) => field.onChange(Number(v))}>
                    <SelectTrigger aria-label="Intervalo">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {(options.data?.intervals ?? [30, 60, 300, 900]).map((s) => (
                        <SelectItem key={s} value={String(s)}>
                          {formatInterval(s)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              {err("intervalSeconds") && <p className="text-xs text-destructive">{err("intervalSeconds")}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="timeoutMs">Timeout (ms)</Label>
              <Input
                id="timeoutMs"
                type="number"
                min={100}
                max={30000}
                step={100}
                {...form.register("timeoutMs", { valueAsNumber: true })}
                aria-invalid={!!err("timeoutMs")}
              />
              {err("timeoutMs") && <p className="text-xs text-destructive">{err("timeoutMs")}</p>}
            </div>
            <div className="space-y-2">
              <Label>Método</Label>
              <Controller
                control={form.control}
                name="httpMethod"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Método HTTP">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="GET">GET</SelectItem>
                      <SelectItem value="HEAD">HEAD</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="expectedStatusCodes">Códigos HTTP esperados</Label>
              <Input
                id="expectedStatusCodes"
                {...form.register("expectedStatusCodes")}
                aria-invalid={!!err("expectedStatusCodes")}
                placeholder="200-299"
              />
              {err("expectedStatusCodes") && <p className="text-xs text-destructive">{err("expectedStatusCodes")}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="degradedLatencyMs">Limite de degradação (ms)</Label>
              <Input
                id="degradedLatencyMs"
                type="number"
                min={1}
                max={60000}
                {...form.register("degradedLatencyMs", {
                  setValueAs: (v: unknown) => (v === "" || v == null ? null : Number(v)),
                })}
                placeholder="Opcional"
              />
              {err("degradedLatencyMs") && <p className="text-xs text-destructive">{err("degradedLatencyMs")}</p>}
            </div>
            <div className="flex items-end gap-3 pb-1">
              <Controller
                control={form.control}
                name="followRedirects"
                render={({ field }) => (
                  <div className="flex items-center gap-2">
                    <Switch id="followRedirects" checked={field.value} onCheckedChange={field.onChange} />
                    <Label htmlFor="followRedirects">Seguir redirecionamentos</Label>
                  </div>
                )}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="failureThreshold">Falhas p/ confirmar indisponível</Label>
              <Input
                id="failureThreshold"
                type="number"
                min={1}
                max={20}
                {...form.register("failureThreshold", { valueAsNumber: true })}
              />
              {err("failureThreshold") && <p className="text-xs text-destructive">{err("failureThreshold")}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="recoveryThreshold">Sucessos p/ confirmar recuperação</Label>
              <Input
                id="recoveryThreshold"
                type="number"
                min={1}
                max={20}
                {...form.register("recoveryThreshold", { valueAsNumber: true })}
              />
              {err("recoveryThreshold") && <p className="text-xs text-destructive">{err("recoveryThreshold")}</p>}
            </div>
          </fieldset>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden />}
              {editing ? "Salvar alterações" : "Cadastrar e iniciar monitoramento"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
