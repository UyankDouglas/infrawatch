import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
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
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ApiError } from "@/services/api";
import { maintenancesApi, organizationApi, resourcesApi } from "@/services/endpoints";
import type { MaintenanceWindow } from "@/types/incidents";

const NONE = "__none__";

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function MaintenanceFormDialog({
  open,
  onOpenChange,
  defaultResourceIds = [],
  onSaved,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  defaultResourceIds?: string[];
  onSaved?: (w: MaintenanceWindow) => void;
}) {
  const queryClient = useQueryClient();
  const resources = useQuery({
    queryKey: ["resources", "list", { forMaintenance: true }],
    queryFn: () => resourcesApi.list({ size: 100 }),
    enabled: open,
  });
  const members = useQuery({ queryKey: ["members"], queryFn: organizationApi.members, enabled: open });

  const [title, setTitle] = useState("");
  const [reason, setReason] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [responsible, setResponsible] = useState(NONE);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      const start = new Date(Date.now() + 10 * 60_000);
      const end = new Date(start.getTime() + 60 * 60_000);
      setTitle("");
      setReason("");
      setStartsAt(toLocalInput(start));
      setEndsAt(toLocalInput(end));
      setSelected(defaultResourceIds);
      setResponsible(NONE);
      setError(null);
    }
  }, [open, defaultResourceIds]);

  const mutation = useMutation({
    mutationFn: () =>
      maintenancesApi.create({
        title: title.trim(),
        reason: reason.trim() || null,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        resourceIds: selected,
        responsibleId: responsible === NONE ? null : responsible,
      }),
    onSuccess: (w) => {
      queryClient.invalidateQueries({ queryKey: ["maintenances"] });
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      toast.success(w.status === "ACTIVE" ? "Manutenção iniciada agora." : "Manutenção agendada.");
      onOpenChange(false);
      onSaved?.(w);
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Falha ao agendar manutenção."),
  });

  const submit = () => {
    setError(null);
    if (!title.trim()) return setError("Informe o título.");
    if (!startsAt || !endsAt) return setError("Informe início e término.");
    if (new Date(endsAt) <= new Date(startsAt)) return setError("O término deve ser posterior ao início.");
    if (selected.length === 0) return setError("Selecione ao menos um recurso.");
    mutation.mutate();
  };

  const toggle = (id: string) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const all = resources.data?.items ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Agendar manutenção</DialogTitle>
          <DialogDescription>
            Durante a janela os recursos ficam em "Em manutenção": as verificações continuam, mas nenhum alerta,
            incidente ou notificação é gerado.
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
          noValidate
        >
          <div className="space-y-2">
            <Label htmlFor="mw-title">Título</Label>
            <Input
              id="mw-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={200}
              placeholder="Ex.: Atualização do banco de dados"
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="mw-start">Início</Label>
              <Input
                id="mw-start"
                type="datetime-local"
                value={startsAt}
                onChange={(e) => setStartsAt(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mw-end">Término</Label>
              <Input id="mw-end" type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <Label>Recursos ({selected.length} selecionado(s))</Label>
            <ScrollArea className="h-40 rounded-md border border-border p-2">
              {all.length === 0 ? (
                <p className="p-2 text-sm text-muted-foreground">Nenhum recurso cadastrado.</p>
              ) : (
                <ul className="space-y-1">
                  {all.map((r) => (
                    <li key={r.id} className="flex items-center gap-2 rounded px-1 py-1 hover:bg-accent/50">
                      <Checkbox
                        id={`mw-r-${r.id}`}
                        checked={selected.includes(r.id)}
                        onCheckedChange={() => toggle(r.id)}
                      />
                      <Label htmlFor={`mw-r-${r.id}`} className="flex-1 cursor-pointer font-normal">
                        {r.name} <span className="text-xs text-muted-foreground">· {r.environmentLabel}</span>
                      </Label>
                    </li>
                  ))}
                </ul>
              )}
            </ScrollArea>
            <div className="flex gap-2 text-xs">
              <button
                type="button"
                className="text-primary hover:underline"
                onClick={() => setSelected(all.map((r) => r.id))}
              >
                Selecionar todos
              </button>
              <button type="button" className="text-muted-foreground hover:underline" onClick={() => setSelected([])}>
                Limpar
              </button>
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label>Responsável</Label>
              <Select value={responsible} onValueChange={setResponsible}>
                <SelectTrigger aria-label="Responsável">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Sem responsável</SelectItem>
                  {(members.data ?? []).map((m) => (
                    <SelectItem key={m.userId} value={m.userId}>
                      {m.fullName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="mw-reason">Motivo</Label>
              <Textarea
                id="mw-reason"
                rows={2}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                maxLength={2000}
              />
            </div>
          </div>
          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="size-4 animate-spin" aria-hidden />}
              Agendar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
