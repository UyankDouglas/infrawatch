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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiError } from "@/services/api";
import { incidentsApi, organizationApi, resourcesApi } from "@/services/endpoints";
import type { Incident } from "@/types/incidents";

const NONE = "__none__";
const schema = z.object({
  title: z.string().trim().min(1, "Informe o título.").max(200),
  description: z.string().max(4000).optional(),
  severity: z.enum(["INFO", "WARNING", "HIGH", "CRITICAL"]),
  resourceId: z.string(),
  assigneeId: z.string(),
});
type FormValues = z.infer<typeof schema>;

export function IncidentFormDialog({
  open,
  onOpenChange,
  defaultResourceId,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  defaultResourceId?: string;
  onCreated?: (incident: Incident) => void;
}) {
  const queryClient = useQueryClient();
  const resources = useQuery({
    queryKey: ["resources", "list", { forIncident: true }],
    queryFn: () => resourcesApi.list({ size: 100 }),
    enabled: open,
  });
  const members = useQuery({ queryKey: ["members"], queryFn: organizationApi.members, enabled: open });
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: "",
      description: "",
      severity: "HIGH",
      resourceId: defaultResourceId ?? NONE,
      assigneeId: NONE,
    },
  });

  useEffect(() => {
    if (open)
      form.reset({
        title: "",
        description: "",
        severity: "HIGH",
        resourceId: defaultResourceId ?? NONE,
        assigneeId: NONE,
      });
  }, [open, defaultResourceId, form]);

  const mutation = useMutation({
    mutationFn: (v: FormValues) =>
      incidentsApi.create({
        title: v.title,
        description: v.description || null,
        severity: v.severity,
        resourceId: v.resourceId === NONE ? null : v.resourceId,
        assigneeId: v.assigneeId === NONE ? null : v.assigneeId,
      }),
    onSuccess: (incident) => {
      queryClient.invalidateQueries({ queryKey: ["incidents"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      toast.success(`Incidente #${incident.number} aberto.`);
      onOpenChange(false);
      onCreated?.(incident);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao abrir incidente."),
  });

  const err = (k: keyof FormValues) => form.formState.errors[k]?.message as string | undefined;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Abrir incidente</DialogTitle>
          <DialogDescription>
            Incidentes manuais nunca são encerrados automaticamente; a resolução é registrada por um operador.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="inc-title">Título</Label>
            <Input id="inc-title" {...form.register("title")} aria-invalid={!!err("title")} />
            {err("title") && <p className="text-xs text-destructive">{err("title")}</p>}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
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
                      <SelectItem value="CRITICAL">Crítico</SelectItem>
                      <SelectItem value="HIGH">Alto</SelectItem>
                      <SelectItem value="WARNING">Atenção</SelectItem>
                      <SelectItem value="INFO">Informativo</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-2">
              <Label>Recurso afetado</Label>
              <Controller
                control={form.control}
                name="resourceId"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Recurso afetado">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE}>Nenhum / vários</SelectItem>
                      {(resources.data?.items ?? []).map((r) => (
                        <SelectItem key={r.id} value={r.id}>
                          {r.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label>Responsável</Label>
            <Controller
              control={form.control}
              name="assigneeId"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
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
              )}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="inc-desc">Descrição</Label>
            <Textarea id="inc-desc" rows={3} {...form.register("description")} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden />}
              Abrir incidente
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
