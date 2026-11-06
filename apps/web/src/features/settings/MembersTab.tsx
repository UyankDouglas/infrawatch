import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Copy, Mail, Trash2, UserPlus, Users } from "lucide-react";
import { toast } from "sonner";
import { EmptyState } from "@/components/EmptyState";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { organizationApi } from "@/services/endpoints";
import { formatDateTime } from "@/lib/format";
import type { MemberRole } from "@/types/api";
import type { InviteCreated } from "@/types/reports";

const ROLES: { value: MemberRole; label: string }[] = [
  { value: "ADMIN", label: "Administrador" },
  { value: "OPERATOR", label: "Operador" },
  { value: "VIEWER", label: "Visualizador" },
];

const INVITE_STATUS: Record<string, { label: string; variant: "default" | "secondary" | "outline" | "destructive" }> = {
  PENDING: { label: "Pendente", variant: "default" },
  ACCEPTED: { label: "Aceito", variant: "secondary" },
  REVOKED: { label: "Revogado", variant: "outline" },
  EXPIRED: { label: "Expirado", variant: "destructive" },
};

export function MembersTab() {
  const { user, hasRole, refresh } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = hasRole("ADMIN");

  const members = useQuery({ queryKey: ["members"], queryFn: organizationApi.members });
  const invites = useQuery({ queryKey: ["invites"], queryFn: organizationApi.invites, enabled: isAdmin });

  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<MemberRole>("OPERATOR");
  const [created, setCreated] = useState<InviteCreated | null>(null);
  const [memberToRemove, setMemberToRemove] = useState<{ userId: string; name: string } | null>(null);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["members"] });
    queryClient.invalidateQueries({ queryKey: ["invites"] });
    queryClient.invalidateQueries({ queryKey: ["audit"] });
  };
  const onError = (fallback: string) => (e: unknown) => toast.error(e instanceof ApiError ? e.message : fallback);

  const invite = useMutation({
    mutationFn: () => organizationApi.invite(inviteEmail.trim(), inviteRole),
    onSuccess: (res) => {
      setInviteEmail("");
      setCreated(res);
      invalidate();
    },
    onError: onError("Falha ao criar convite."),
  });
  const revoke = useMutation({
    mutationFn: (id: string) => organizationApi.revokeInvite(id),
    onSuccess: () => {
      invalidate();
      toast.success("Convite revogado.");
    },
    onError: onError("Falha ao revogar convite."),
  });
  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: MemberRole }) => organizationApi.changeRole(userId, role),
    onSuccess: async (res) => {
      invalidate();
      toast.success(`Perfil atualizado para ${res.roleLabel}.`);
      if (res.userId === user?.id) await refresh();
    },
    onError: onError("Falha ao alterar perfil."),
  });
  const remove = useMutation({
    mutationFn: (userId: string) => organizationApi.removeMember(userId),
    onSuccess: () => {
      setMemberToRemove(null);
      invalidate();
      toast.success("Membro removido.");
    },
    onError: onError("Falha ao remover membro."),
  });

  const copyLink = async () => {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.link);
      toast.success("Link copiado.");
    } catch {
      toast.error("Não foi possível copiar. Selecione e copie o link manualmente.");
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Membros da organização</CardTitle>
          <CardDescription>
            Perfis: Administrador (gerencia tudo), Operador (opera e reconhece), Visualizador (somente leitura). A
            organização precisa manter ao menos um administrador.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {members.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : (members.data?.length ?? 0) === 0 ? (
            <EmptyState compact icon={Users} title="Nenhum membro" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome</TableHead>
                  <TableHead>E-mail</TableHead>
                  <TableHead>Perfil</TableHead>
                  <TableHead>Desde</TableHead>
                  {isAdmin && <TableHead />}
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.data!.map((m) => (
                  <TableRow key={m.userId}>
                    <TableCell className="font-medium">
                      {m.fullName}
                      {m.userId === user?.id && <span className="ml-2 text-xs text-muted-foreground">(você)</span>}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{m.email}</TableCell>
                    <TableCell>
                      {isAdmin ? (
                        <Select
                          value={m.role}
                          onValueChange={(v) => changeRole.mutate({ userId: m.userId, role: v as MemberRole })}
                        >
                          <SelectTrigger className="h-8 w-[170px]" aria-label={`Perfil de ${m.fullName}`}>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {ROLES.map((r) => (
                              <SelectItem key={r.value} value={r.value}>
                                {r.label}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      ) : (
                        <Badge variant={m.role === "ADMIN" ? "default" : "secondary"}>{m.roleLabel}</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{formatDateTime(m.since, "dd/MM/yyyy")}</TableCell>
                    {isAdmin && (
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Remover ${m.fullName}`}
                          disabled={m.userId === user?.id}
                          onClick={() => setMemberToRemove({ userId: m.userId, name: m.fullName })}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {isAdmin && (
        <Card>
          <CardHeader>
            <CardTitle>Convites</CardTitle>
            <CardDescription>
              O link do convite é exibido uma única vez, expira em 7 dias e só vale para o e-mail informado.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form
              className="grid gap-2 sm:grid-cols-[1fr_200px_auto]"
              onSubmit={(e) => {
                e.preventDefault();
                if (inviteEmail.trim()) invite.mutate();
              }}
            >
              <div className="space-y-1">
                <Label htmlFor="invite-email" className="sr-only">
                  E-mail do convidado
                </Label>
                <Input
                  id="invite-email"
                  type="email"
                  placeholder="pessoa@empresa.com"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  required
                />
              </div>
              <Select value={inviteRole} onValueChange={(v) => setInviteRole(v as MemberRole)}>
                <SelectTrigger aria-label="Perfil do convidado">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ROLES.map((r) => (
                    <SelectItem key={r.value} value={r.value}>
                      {r.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button type="submit" disabled={!inviteEmail.trim() || invite.isPending}>
                <UserPlus className="size-4" aria-hidden /> Convidar
              </Button>
            </form>

            {invites.isLoading ? (
              <Skeleton className="h-16 w-full" />
            ) : (invites.data?.length ?? 0) === 0 ? (
              <EmptyState compact icon={Mail} title="Nenhum convite emitido" />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>E-mail</TableHead>
                    <TableHead>Perfil</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Expira em</TableHead>
                    <TableHead>Convidado por</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {invites.data!.map((i) => (
                    <TableRow key={i.id}>
                      <TableCell className="font-medium">{i.email}</TableCell>
                      <TableCell>{i.roleLabel}</TableCell>
                      <TableCell>
                        <Badge variant={INVITE_STATUS[i.status]?.variant ?? "outline"}>
                          {INVITE_STATUS[i.status]?.label ?? i.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatDateTime(i.expiresAt, "dd/MM/yyyy HH:mm")}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{i.invitedBy ?? "—"}</TableCell>
                      <TableCell className="text-right">
                        {i.status === "PENDING" && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => revoke.mutate(i.id)}
                            disabled={revoke.isPending}
                          >
                            Revogar
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}

      <Dialog open={!!created} onOpenChange={(open) => !open && setCreated(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Convite criado</DialogTitle>
            <DialogDescription>{created?.message}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="invite-link">Link do convite para {created?.invite.email}</Label>
            <div className="flex gap-2">
              <Input id="invite-link" readOnly value={created?.link ?? ""} onFocus={(e) => e.currentTarget.select()} />
              <Button type="button" variant="outline" onClick={copyLink} aria-label="Copiar link">
                <Copy className="size-4" />
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              O envio por e-mail depende de um canal SMTP configurado; em desenvolvimento, compartilhe o link
              manualmente.
            </p>
          </div>
          <DialogFooter>
            <Button onClick={() => setCreated(null)}>Fechar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!memberToRemove}
        onOpenChange={(open) => !open && setMemberToRemove(null)}
        title="Remover membro"
        description={`${memberToRemove?.name} perderá o acesso a esta organização. Deseja continuar?`}
        confirmLabel="Remover"
        destructive
        pending={remove.isPending}
        onConfirm={() => memberToRemove && remove.mutate(memberToRemove.userId)}
      />
    </div>
  );
}
