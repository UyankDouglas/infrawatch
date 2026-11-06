import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
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
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiError } from "@/services/api";
import { alertsApi, groupsApi, resourcesApi } from "@/services/endpoints";
import type { AlertRule, AlertRuleInput } from "@/types/alerts";

const ALL = "__all__";
const schema = z.object({
  name: z.string().trim().min(1, "Informe o nome.").max(120),
  description: z.string().max(500).optional(),
  metric: z.enum(["STATUS_DOWN", "STATUS_DEGRADED", "LATENCY", "CPU", "MEMORY", "DISK", "AGENT_NO_DATA"]),
  operator: z.enum(["GT", "GTE", "LT", "LTE"]),
  threshold: z.number({ error: "Informe um número." }).nullable(),
  durationSeconds: z.number({ error: "Informe um número." }).int().min(0).max(86400),
  occurrences: z.number({ error: "Informe um número." }).int().min(1).max(100),
  severity: z.enum(["INFO", "WARNING", "HIGH", "CRITICAL"]),
  scope: z.string(),
  enabled: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

const DEFAULTS: FormValues = {
  name: "",
  description: "",
  metric: "STATUS_DOWN",
  operator: "GT",
  threshold: null,
  durationSeconds: 0,
  occurrences: 1,
  severity: "HIGH",
  scope: ALL,
  enabled: true,
};

function toValues(r: AlertRule): FormValues {
  return {
    name: r.name,
    description: r.description ?? "",
    metric: r.metric,
    operator: r.operator ?? "GT",
    threshold: r.threshold,
    durationSeconds: r.durationSeconds,
    occurrences: r.occurrences,
    severity: r.severity,
    scope: r.resourceId ? `r:${r.resourceId}` : r.groupId ? `g:${r.groupId}` : ALL,
    enabled: r.enabled,
  };
}

export function AlertRuleDialog({
  open,
  onOpenChange,
  rule,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  rule?: AlertRule | null;
}) {
  const queryClient = useQueryClient();
  const editing = !!rule;
  const options = useQuery({
    queryKey: ["alerts", "rule-options"],
    queryFn: alertsApi.ruleOptions,
    staleTime: Infinity,
    enabled: open,
  });
  const resources = useQuery({
    queryKey: ["resources", "list", { forRules: true }],
    queryFn: () => resourcesApi.list({ size: 100 }),
    enabled: open,
  });
  const groups = useQuery({ queryKey: ["groups"], queryFn: groupsApi.list, enabled: open });
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: DEFAULTS });

  useEffect(() => {
    if (open) form.reset(rule ? toValues(rule) : DEFAULTS);
  }, [open, rule, form]);

  const metric = form.watch("metric");
  const metricMeta = options.data?.metrics.find((m) => m.value === metric);
  const numeric = metricMeta?.numeric ?? ["LATENCY", "CPU", "MEMORY", "DISK"].includes(metric);

  const mutation = useMutation({
    mutationFn: (v: FormValues) => {
      const input: AlertRuleInput = {
        name: v.name,
        description: v.description || null,
        metric: v.metric,
        operator: numeric ? v.operator : null,
        threshold: numeric ? v.threshold : null,
        durationSeconds: v.durationSeconds,
        occurrences: v.occurrences,
        severity: v.severity,
        resourceId: v.scope.startsWith("r:") ? v.scope.slice(2) : null,
        groupId: v.scope.startsWith("g:") ? v.scope.slice(2) : null,
        clearScope: editing && v.scope === ALL ? true : undefined,
        enabled: v.enabled,
      };
      return editing ? alertsApi.updateRule(rule!.id, input) : alertsApi.createRule(input);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["alerts"] });
      toast.success(editing ? "Regra atualizada." : "Regra criada.");
      onOpenChange(false);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.errors) {
        Object.entries(e.errors).forEach(([f, m]) => form.setError(f as keyof FormValues, { message: m }));
      } else if (e instanceof ApiError && e.code.startsWith("rule_threshold")) {
        form.setError("threshold", { message: e.message });
      } else toast.error(e instanceof ApiError ? e.message : "Falha ao salvar a regra.");
    },
  });

  const err = (k: keyof FormValues) => form.formState.errors[k]?.message as string | undefined;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{editing ? "Editar regra de alerta" : "Nova regra de alerta"}</DialogTitle>
          <DialogDescription>
            Regras só existem para métricas que o InfraWatch coleta. Um alerta permanece único enquanto a condição
            persistir.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="rule-name">Nome</Label>
            <Input id="rule-name" {...form.register("name")} aria-invalid={!!err("name")} />
            {err("name") && <p className="text-xs text-destructive">{err("name")}</p>}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label>Métrica / condição</Label>
              <Controller
                control={form.control}
                name="metric"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange} disabled={editing}>
                    <SelectTrigger aria-label="Métrica">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {(options.data?.metrics ?? []).map((m) => (
                        <SelectItem key={m.value} value={m.value}>
                          {m.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-2">
              <Label>Severidade</Label>
              <Controller
                control={form.control}
                name="severity"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Severidade">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {(options.data?.severities ?? []).map((s) => (
                        <SelectItem key={s.value} value={s.value}>
                          {s.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>
          {numeric && (
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="space-y-2">
                <Label>Operador</Label>
                <Controller
                  control={form.control}
                  name="operator"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger aria-label="Operador">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {(options.data?.operators ?? []).map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="rule-threshold">Limite ({metricMeta?.unit ?? ""})</Label>
                <Input
                  id="rule-threshold"
                  type="number"
                  step="0.1"
                  {...form.register("threshold", { setValueAs: (v) => (v === "" || v == null ? null : Number(v)) })}
                  aria-invalid={!!err("threshold")}
                />
                {err("threshold") && <p className="text-xs text-destructive">{err("threshold")}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="rule-occ">Ocorrências consecutivas</Label>
                <Input
                  id="rule-occ"
                  type="number"
                  min={1}
                  max={100}
                  {...form.register("occurrences", { valueAsNumber: true })}
                />
              </div>
            </div>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            {numeric && (
              <div className="space-y-2">
                <Label htmlFor="rule-duration">Duração mínima da condição (s)</Label>
                <Input
                  id="rule-duration"
                  type="number"
                  min={0}
                  max={86400}
                  {...form.register("durationSeconds", { valueAsNumber: true })}
                />
              </div>
            )}
            <div className="space-y-2">
              <Label>Aplicar a</Label>
              <Controller
                control={form.control}
                name="scope"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Escopo">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={ALL}>Todos os recursos</SelectItem>
                      {(groups.data ?? []).map((g) => (
                        <SelectItem key={g.id} value={`g:${g.id}`}>
                          Grupo: {g.name}
                        </SelectItem>
                      ))}
                      {(resources.data?.items ?? []).map((r) => (
                        <SelectItem key={r.id} value={`r:${r.id}`}>
                          Recurso: {r.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="rule-desc">Descrição</Label>
            <Textarea id="rule-desc" rows={2} {...form.register("description")} />
          </div>
          <Controller
            control={form.control}
            name="enabled"
            render={({ field }) => (
              <div className="flex items-center gap-2">
                <Switch id="rule-enabled" checked={field.value} onCheckedChange={field.onChange} />
                <Label htmlFor="rule-enabled">Regra ativa</Label>
              </div>
            )}
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden />}
              {editing ? "Salvar" : "Criar regra"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
