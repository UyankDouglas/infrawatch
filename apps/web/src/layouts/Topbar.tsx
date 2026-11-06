import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Building2, Check, ChevronDown, LogOut, Menu, Search } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { ThemeToggle } from "@/components/ThemeToggle";
import { StatusDot } from "@/components/StatusBadge";
import { useAuth } from "@/features/auth/AuthProvider";
import { organizationApi, resourcesApi } from "@/services/endpoints";
import { cn } from "@/lib/utils";

function initials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join("");
}

export function Topbar({ onOpenMobileMenu }: { onOpenMobileMenu: () => void }) {
  const { user, logout, refresh } = useAuth();
  const navigate = useNavigate();
  const [term, setTerm] = useState("");
  const [open, setOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const search = useQuery({
    queryKey: ["resources", "search", term],
    queryFn: ({ signal }) => resourcesApi.list({ search: term, size: 6 }, signal),
    enabled: term.trim().length >= 2,
    staleTime: 10_000,
  });

  const switchOrg = useMutation({
    mutationFn: (id: string) => organizationApi.switch(id),
    onSuccess: async (org) => {
      await refresh();
      toast.success(`Organização ativa: ${org.name}`);
      navigate("/");
    },
  });

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const org = user?.currentOrganization;
  const results = search.data?.items ?? [];

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-background/85 px-4 backdrop-blur sm:px-6">
      <Button variant="ghost" size="icon" className="md:hidden" onClick={onOpenMobileMenu} aria-label="Abrir menu">
        <Menu className="size-5" />
      </Button>

      <div className="relative hidden min-w-0 flex-1 sm:block sm:max-w-md">
        <Search
          className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
        <input
          ref={inputRef}
          type="search"
          role="combobox"
          aria-expanded={open && results.length > 0}
          aria-controls="busca-resultados"
          aria-label="Buscar recursos"
          placeholder="Buscar recursos… (Ctrl+K)"
          value={term}
          onChange={(e) => {
            setTerm(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && term.trim()) {
              navigate(`/recursos?search=${encodeURIComponent(term.trim())}`);
              setOpen(false);
            }
            if (e.key === "Escape") setOpen(false);
          }}
          className="h-9 w-full rounded-md border border-input bg-card pr-3 pl-9 text-sm outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
        />
        {open && term.trim().length >= 2 && (
          <ul
            id="busca-resultados"
            role="listbox"
            className="absolute top-full right-0 left-0 z-40 mt-1 overflow-hidden rounded-md border border-border bg-popover shadow-lg"
          >
            {search.isFetching && results.length === 0 && (
              <li className="px-3 py-2 text-sm text-muted-foreground">Buscando…</li>
            )}
            {!search.isFetching && results.length === 0 && (
              <li className="px-3 py-2 text-sm text-muted-foreground">Nenhum recurso encontrado.</li>
            )}
            {results.map((r) => (
              <li key={r.id} role="option" aria-selected={false}>
                <button
                  type="button"
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    navigate(`/recursos/${r.id}`);
                    setOpen(false);
                    setTerm("");
                  }}
                >
                  <StatusDot status={r.status} />
                  <span className="flex-1 truncate">{r.name}</span>
                  <span className="truncate text-xs text-muted-foreground">{r.targetUrl}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        {org && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" className="max-w-[220px] gap-2">
                <Building2 className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                <span className="truncate">{org.name}</span>
                <span className="hidden rounded bg-muted px-1.5 py-0.5 text-[10px] font-semibold text-muted-foreground sm:inline">
                  {org.roleLabel}
                </span>
                <ChevronDown className="size-3.5 text-muted-foreground" aria-hidden />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-64">
              <DropdownMenuLabel>Organização ativa</DropdownMenuLabel>
              {user.organizations.map((o) => (
                <DropdownMenuItem
                  key={o.id}
                  onSelect={() => o.id !== org.id && switchOrg.mutate(o.id)}
                  className={cn("flex items-center gap-2", o.id === org.id && "font-medium")}
                >
                  <span className="flex-1 truncate">{o.name}</span>
                  <span className="text-xs text-muted-foreground">{o.roleLabel}</span>
                  {o.id === org.id && <Check className="size-4 text-primary" aria-hidden />}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
        <ThemeToggle />
        {user && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="rounded-full" aria-label="Menu do usuário">
                <Avatar className="size-8">
                  <AvatarFallback className="bg-primary/15 text-xs font-semibold text-primary">
                    {initials(user.fullName)}
                  </AvatarFallback>
                </Avatar>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuLabel className="font-normal">
                <p className="truncate text-sm font-medium">{user.fullName}</p>
                <p className="truncate text-xs text-muted-foreground">{user.email}</p>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => navigate("/configuracoes")}>Configurações</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onSelect={async () => {
                  await logout();
                  navigate("/login");
                }}
                className="text-destructive focus:text-destructive"
              >
                <LogOut className="size-4" aria-hidden /> Sair
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>
    </header>
  );
}
