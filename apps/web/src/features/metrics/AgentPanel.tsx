import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, KeyRound, RefreshCw, ShieldOff, Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { agentsApi } from "@/services/endpoints";
import { formatDateTime, formatRelative } from "@/lib/format";
import type { AgentTokenResponse } from "@/types/metrics";

function TokenReveal({ result, onDismiss }: { result: AgentTokenResponse; onDismiss: () => void }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(result.token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Não foi possível copiar. Selecione o texto manualmente.");
    }
  };
  const dockerCmd = `docker run -d --name infrawatch-agent \\
  -e INFRAWATCH_API_URL=${window.location.origin} \\
  -e INFRAWATCH_AGENT_TOKEN=${result.token} \\
  infrawatch-agent`;
  return (
    <Alert>
      <KeyRound className="size-4" />
      <AlertTitle>Token do agente gerado</AlertTitle>
      <AlertDescription className="space-y-3">
        <p>{result.message}</p>
        <div className="flex items-center gap-2">
          <code
            className="flex-1 overflow-x-auto rounded bg-muted px-2 py-1 font-mono text-xs"
            data-testid="agent-token"
          >
            {result.token}
          </code>
          <Button size="sm" variant="outline" onClick={copy} aria-label="Copiar token">
            {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
          </Button>
        </div>
        <details className="text-xs">
          <summary className="cursor-pointer text-muted-foreground">Como executar o agente (Docker)</summary>
          <pre className="mt-2 overflow-x-auto rounded bg-muted p-2 font-mono text-[11px] leading-relaxed">
            {dockerCmd}
          </pre>
        </details>
        <Button size="sm" variant="ghost" onClick={onDismiss}>
          Já guardei o token
        </Button>
      </AlertDescription>
    </Alert>
  );
}

export function AgentPanel({ resourceId, resourceName }: { resourceId: string; resourceName: string }) {
  const { hasRole } = useAuth();
  const queryClient = useQueryClient();
  const [name, setName] = useState(
    `agente-${resourceName
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .slice(0, 30)}`,
  );
  const [interval, setInterval] = useState(30);
  const [revealed, setRevealed] = useState<AgentTokenResponse | null>(null);
  const [confirm, setConfirm] = useState<"rotate" | "revoke" | null>(null);

  const agent = useQuery({
    queryKey: ["resources", "agent", resourceId],
    queryFn: () => agentsApi.forResource(resourceId),
    refetchInterval: 15_000,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["resources"] });
  };

  const register = useMutation({
    mutationFn: () => agentsApi.register({ resourceId, name: name.trim(), intervalSeconds: interval }),
    onSuccess: (r) => {
      setRevealed(r);
      invalidate();
      toast.success("Agente registrado.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao registrar agente."),
  });
  const rotate = useMutation({
    mutationFn: (id: string) => agentsApi.rotate(id),
    onSuccess: (r) => {
      setRevealed(r);
      setConfirm(null);
      invalidate();
      toast.success("Token rotacionado. O token anterior foi revogado.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao rotacionar token."),
  });
  const revoke = useMutation({
    mutationFn: (id: string) => agentsApi.revoke(id),
    onSuccess: () => {
      setConfirm(null);
      invalidate();
      toast.success("Agente revogado. Amostras com o token antigo serão recusadas.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao revogar agente."),
  });

  const a = agent.data ?? null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Agente autorizado</CardTitle>
        <CardDescription>
          O agente roda no servidor administrado por você e apenas envia métricas. Ele não recebe comandos nem expõe
          acesso remoto.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {revealed && <TokenReveal result={revealed} onDismiss={() => setRevealed(null)} />}

        {agent.isLoading ? (
          <Skeleton className="h-24 w-full" />
        ) : !a ? (
          hasRole("ADMIN") ? (
            <form
              className="grid gap-3 sm:grid-cols-[1fr_160px_auto] sm:items-end"
              onSubmit={(e) => {
                e.preventDefault();
                if (name.trim()) register.mutate();
              }}
            >
              <div className="space-y-2">
                <Label htmlFor="agent-name">Nome do agente</Label>
                <Input id="agent-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={120} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="agent-interval">Intervalo (s)</Label>
                <Input
                  id="agent-interval"
                  type="number"
                  min={10}
                  max={3600}
                  value={interval}
                  onChange={(e) => setInterval(Number(e.target.value))}
                />
              </div>
              <Button type="submit" disabled={register.isPending || !name.trim()}>
                <Plus className="size-4" aria-hidden /> Registrar agente
              </Button>
            </form>
          ) : (
            <p className="text-sm text-muted-foreground">
              Nenhum agente registrado. Peça a um administrador para registrar um.
            </p>
          )
        ) : (
          <div className="space-y-4">
            <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <dt className="text-muted-foreground">Nome</dt>
                <dd className="font-medium">{a.name}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Situação</dt>
                <dd>
                  {a.status === "REVOKED" ? (
                    <Badge variant="destructive">Revogado</Badge>
                  ) : a.stale ? (
                    <Badge className="bg-status-unknown/15 text-status-unknown">Silencioso</Badge>
                  ) : a.lastSeenAt ? (
                    <Badge className="bg-status-up/15 text-status-up">Reportando</Badge>
                  ) : (
                    <Badge variant="secondary">Aguardando primeira amostra</Badge>
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Intervalo</dt>
                <dd className="font-medium">{a.intervalSeconds} s</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Último contato</dt>
                <dd className="font-medium" title={a.lastSeenAt ?? undefined}>
                  {a.lastSeenAt ? formatRelative(a.lastSeenAt) : "nunca"}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Host / versão</dt>
                <dd className="font-medium">
                  {a.hostname ?? "—"} {a.agentVersion ? `· v${a.agentVersion}` : ""}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Credencial</dt>
                <dd className="font-mono text-xs">{a.tokenPrefix ? `iwa_${a.tokenPrefix}.…` : "nenhuma ativa"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Registrado em</dt>
                <dd className="font-medium">{formatDateTime(a.createdAt, "dd/MM/yyyy HH:mm")}</dd>
              </div>
            </dl>
            {hasRole("ADMIN") && (
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" size="sm" onClick={() => setConfirm("rotate")}>
                  <RefreshCw className="size-4" aria-hidden /> Rotacionar token
                </Button>
                {a.status === "ACTIVE" && (
                  <Button variant="destructive" size="sm" onClick={() => setConfirm("revoke")}>
                    <ShieldOff className="size-4" aria-hidden /> Revogar
                  </Button>
                )}
              </div>
            )}
          </div>
        )}
      </CardContent>

      <ConfirmDialog
        open={confirm === "rotate"}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Rotacionar token"
        description="Um novo token será gerado e o atual deixará de funcionar imediatamente. Atualize o agente com o novo token."
        confirmLabel="Rotacionar"
        pending={rotate.isPending}
        onConfirm={() => a && rotate.mutate(a.id)}
      />
      <ConfirmDialog
        open={confirm === "revoke"}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Revogar agente"
        description="O agente não poderá mais enviar amostras. As métricas já recebidas são mantidas."
        confirmLabel="Revogar"
        destructive
        pending={revoke.isPending}
        onConfirm={() => a && revoke.mutate(a.id)}
      />
    </Card>
  );
}
