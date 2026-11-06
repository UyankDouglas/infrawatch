import { useState } from "react";
import { useNavigate } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ScrollArea } from "@/components/ui/scroll-area";
import { EmptyState } from "@/components/EmptyState";
import { SeverityBadge } from "./SeverityBadge";
import { notificationsApi } from "@/services/endpoints";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { AppNotification } from "@/types/alerts";

export function NotificationsBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const unread = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: notificationsApi.unreadCount,
    refetchInterval: 30_000,
  });
  const list = useQuery({
    queryKey: ["notifications", "list", { page: 0, size: 15 }],
    queryFn: () => notificationsApi.list(false, 0, 15),
    enabled: open,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["notifications"] });
  const markRead = useMutation({ mutationFn: (id: string) => notificationsApi.markRead(id), onSuccess: invalidate });
  const markAll = useMutation({ mutationFn: () => notificationsApi.markAllRead(), onSuccess: invalidate });

  const count = unread.data?.unread ?? 0;

  const openNotification = (n: AppNotification) => {
    if (!n.readAt) markRead.mutate(n.id);
    setOpen(false);
    if (n.link) navigate(n.link);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          aria-label={count > 0 ? `${count} notificações não lidas` : "Notificações"}
        >
          <Bell className="size-4" />
          {count > 0 && (
            <span
              className="absolute -top-0.5 -right-0.5 flex min-w-[18px] items-center justify-center rounded-full bg-status-down px-1 text-[10px] font-bold text-white"
              data-testid="unread-badge"
            >
              {count > 99 ? "99+" : count}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[380px] p-0">
        <div className="flex items-center justify-between border-b border-border px-3 py-2">
          <p className="text-sm font-semibold">Notificações</p>
          <Button
            variant="ghost"
            size="sm"
            disabled={count === 0 || markAll.isPending}
            onClick={() => markAll.mutate()}
          >
            <CheckCheck className="size-4" aria-hidden /> Marcar todas como lidas
          </Button>
        </div>
        <ScrollArea className="max-h-[420px]">
          {list.isLoading ? (
            <p className="p-4 text-sm text-muted-foreground">Carregando…</p>
          ) : (list.data?.items.length ?? 0) === 0 ? (
            <div className="p-3">
              <EmptyState
                compact
                icon={Bell}
                title="Nenhuma notificação"
                description="Você será avisado sobre indisponibilidades, recuperações e alertas."
              />
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {list.data!.items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    onClick={() => openNotification(n)}
                    className={cn(
                      "flex w-full flex-col gap-1 px-3 py-2.5 text-left hover:bg-accent/50",
                      !n.readAt && "bg-primary/5",
                    )}
                  >
                    <div className="flex items-center gap-2">
                      {!n.readAt && <span className="status-dot size-2 bg-primary" aria-label="Não lida" />}
                      <SeverityBadge severity={n.severity} />
                      <span className="ml-auto text-[11px] text-muted-foreground">{formatRelative(n.createdAt)}</span>
                    </div>
                    <p className="text-sm font-medium leading-snug">{n.title}</p>
                    {n.message && <p className="line-clamp-2 text-xs text-muted-foreground">{n.message}</p>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </ScrollArea>
      </PopoverContent>
    </Popover>
  );
}
