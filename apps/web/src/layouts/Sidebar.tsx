import { NavLink } from "react-router";
import {
  Activity,
  BellRing,
  ChartColumnBig,
  ChevronsLeft,
  ChevronsRight,
  LayoutDashboard,
  Server,
  Settings,
  Siren,
  Wrench,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

const NAV = [
  { to: "/", label: "Visão Geral", icon: LayoutDashboard, end: true },
  { to: "/recursos", label: "Recursos Monitorados", icon: Server },
  { to: "/alertas", label: "Alertas", icon: BellRing },
  { to: "/incidentes", label: "Incidentes", icon: Siren },
  { to: "/manutencoes", label: "Manutenções", icon: Wrench },
  { to: "/relatorios", label: "Relatórios", icon: ChartColumnBig, soon: "F5" },
  { to: "/configuracoes", label: "Configurações", icon: Settings },
];

interface Props {
  collapsed: boolean;
  onToggle: () => void;
  mobile?: boolean;
}

export function Sidebar({ collapsed, onToggle, mobile }: Props) {
  return (
    <nav
      aria-label="Navegação principal"
      className="sticky top-0 flex h-screen flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground"
    >
      <div
        className={cn(
          "flex h-16 items-center border-b border-sidebar-border px-3",
          collapsed ? "justify-center" : "gap-3 px-4",
        )}
      >
        <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-md shadow-primary/30">
          <Activity className="size-4" aria-hidden />
        </div>
        {!collapsed && (
          <div className="min-w-0 flex-1 leading-tight">
            <p className="truncate font-semibold tracking-tight">InfraWatch</p>
            <p className="truncate text-[11px] text-muted-foreground">Infrastructure Monitoring</p>
          </div>
        )}
        {mobile && (
          <Button variant="ghost" size="icon" onClick={onToggle} aria-label="Fechar menu">
            <X className="size-4" />
          </Button>
        )}
      </div>

      <ul className="flex-1 space-y-1 overflow-y-auto p-2">
        {NAV.map((item) => {
          const link = (
            <NavLink
              to={item.to}
              end={item.end}
              onClick={mobile ? onToggle : undefined}
              className={({ isActive }) =>
                cn(
                  "group flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors outline-none",
                  "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-2 focus-visible:ring-sidebar-ring",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground shadow-[inset_3px_0_0_0_var(--sidebar-primary)]"
                    : "text-muted-foreground",
                  collapsed && "justify-center px-0",
                )
              }
            >
              <item.icon className="size-4 shrink-0" aria-hidden />
              {!collapsed && <span className="flex-1 truncate">{item.label}</span>}
              {!collapsed && item.soon && (
                <span
                  className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-semibold text-muted-foreground"
                  title="Fase futura"
                >
                  {item.soon}
                </span>
              )}
            </NavLink>
          );
          return (
            <li key={item.to}>
              {collapsed ? (
                <Tooltip>
                  <TooltipTrigger asChild>{link}</TooltipTrigger>
                  <TooltipContent side="right">{item.label}</TooltipContent>
                </Tooltip>
              ) : (
                link
              )}
            </li>
          );
        })}
      </ul>

      {!mobile && (
        <div className="border-t border-sidebar-border p-2">
          <Button
            variant="ghost"
            size="sm"
            className={cn("w-full text-muted-foreground", collapsed ? "justify-center" : "justify-start")}
            onClick={onToggle}
            aria-label={collapsed ? "Expandir menu lateral" : "Recolher menu lateral"}
            aria-expanded={!collapsed}
          >
            {collapsed ? <ChevronsRight className="size-4" /> : <ChevronsLeft className="size-4" />}
            {!collapsed && <span>Recolher</span>}
          </Button>
        </div>
      )}
    </nav>
  );
}
