import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FolderTree, Plus, ScrollText, Trash2, Users } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { auditApi, groupsApi, organizationApi } from "@/services/endpoints";
import { formatDateTime } from "@/lib/format";

export function SettingsPage() {
  const { user, hasRole } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = hasRole("ADMIN");

  const members = useQuery({ queryKey: ["members"], queryFn: organizationApi.members });
  const groups = useQuery({ queryKey: ["groups"], queryFn: groupsApi.list });
  const audit = useQuery({ queryKey: ["audit"], queryFn: () => auditApi.list(0, 50), enabled: isAdmin });

  const [groupName, setGroupName] = useState("");
  const [groupToDelete, setGroupToDelete] = useState<{ id: string; name: string } | null>(null);

  const createGroup = useMutation({
    mutationFn: () => groupsApi.create({ name: groupName }),
    onSuccess: () => {
      setGroupName("");
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      toast.success("Grupo criado.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao criar grupo."),
  });
  const deleteGroup = useMutation({
    mutationFn: (id: string) => groupsApi.remove(id),
    onSuccess: () => {
      setGroupToDelete(null);
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      toast.success("Grupo removido.");
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Falha ao remover grupo."),
  });

  return (
    <>
      <PageHeader title="Configurações" description={`Organização ativa: ${user?.currentOrganization?.name ?? "—"}`} />
      <Tabs defaultValue="members">
        <TabsList>
          <TabsTrigger value="members">Membros</TabsTrigger>
          <TabsTrigger value="groups">Grupos</TabsTrigger>
          {isAdmin && <TabsTrigger value="audit">Auditoria</TabsTrigger>}
        </TabsList>

        <TabsContent value="members">
          <Card>
            <CardHeader>
              <CardTitle>Membros da organização</CardTitle>
              <CardDescription>
                Perfis: Administrador (gerencia tudo), Operador (opera e reconhece), Visualizador (somente leitura).
                Convites e alteração de perfil chegam na Fase 5.
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
                          <Badge variant={m.role === "ADMIN" ? "default" : "secondary"}>{m.roleLabel}</Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">{formatDateTime(m.since, "dd/MM/yyyy")}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="groups">
          <Card>
            <CardHeader>
              <CardTitle>Grupos de recursos</CardTitle>
              <CardDescription>Agrupe recursos por sistema, time ou domínio para facilitar filtros.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {isAdmin && (
                <form
                  className="flex gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (groupName.trim()) createGroup.mutate();
                  }}
                >
                  <Input
                    aria-label="Nome do novo grupo"
                    placeholder="Nome do grupo"
                    value={groupName}
                    onChange={(e) => setGroupName(e.target.value)}
                    maxLength={100}
                  />
                  <Button type="submit" disabled={!groupName.trim() || createGroup.isPending}>
                    <Plus className="size-4" aria-hidden /> Criar
                  </Button>
                </form>
              )}
              {groups.isLoading ? (
                <Skeleton className="h-20 w-full" />
              ) : (groups.data?.length ?? 0) === 0 ? (
                <EmptyState
                  compact
                  icon={FolderTree}
                  title="Nenhum grupo criado"
                  description="Grupos são opcionais, mas ajudam a organizar dezenas de recursos."
                />
              ) : (
                <ul className="divide-y divide-border rounded-md border border-border">
                  {groups.data!.map((g) => (
                    <li key={g.id} className="flex items-center gap-3 px-3 py-2.5">
                      <FolderTree className="size-4 text-muted-foreground" aria-hidden />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{g.name}</p>
                        {g.description && <p className="truncate text-xs text-muted-foreground">{g.description}</p>}
                      </div>
                      <span className="text-xs text-muted-foreground">{g.resourceCount} recurso(s)</span>
                      {isAdmin && (
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Remover grupo ${g.name}`}
                          onClick={() => setGroupToDelete({ id: g.id, name: g.name })}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {isAdmin && (
          <TabsContent value="audit">
            <Card>
              <CardHeader>
                <CardTitle>Auditoria</CardTitle>
                <CardDescription>Ações relevantes registradas nesta organização.</CardDescription>
              </CardHeader>
              <CardContent>
                {audit.isLoading ? (
                  <Skeleton className="h-24 w-full" />
                ) : (audit.data?.items.length ?? 0) === 0 ? (
                  <EmptyState compact icon={ScrollText} title="Nenhum registro de auditoria" />
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Quando</TableHead>
                        <TableHead>Ação</TableHead>
                        <TableHead>Detalhes</TableHead>
                        <TableHead>IP</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {audit.data!.items.map((a) => (
                        <TableRow key={a.id}>
                          <TableCell className="whitespace-nowrap text-muted-foreground">
                            {formatDateTime(a.createdAt)}
                          </TableCell>
                          <TableCell>
                            <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{a.action}</code>
                          </TableCell>
                          <TableCell className="text-sm">{a.details ?? "—"}</TableCell>
                          <TableCell className="font-mono text-xs text-muted-foreground">
                            {a.ipAddress ?? "—"}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        )}
      </Tabs>

      <ConfirmDialog
        open={!!groupToDelete}
        onOpenChange={(open) => !open && setGroupToDelete(null)}
        title="Remover grupo"
        description={`Os recursos do grupo "${groupToDelete?.name}" ficarão sem grupo. Deseja continuar?`}
        confirmLabel="Remover"
        destructive
        pending={deleteGroup.isPending}
        onConfirm={() => groupToDelete && deleteGroup.mutate(groupToDelete.id)}
      />
    </>
  );
}
