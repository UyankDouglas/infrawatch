import { Outlet } from "react-router";
import { Activity, Radar, ShieldCheck, Timer } from "lucide-react";
import { ThemeToggle } from "@/components/ThemeToggle";

export function AuthLayout() {
  return (
    <div className="grid min-h-screen bg-background lg:grid-cols-[1.1fr_1fr]">
      <aside className="relative hidden overflow-hidden bg-sidebar text-sidebar-foreground lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-40 [background-image:radial-gradient(circle_at_20%_20%,#3b82f633,transparent_45%),radial-gradient(circle_at_80%_70%,#06b6d426,transparent_40%)]"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-[0.06] [background-image:linear-gradient(#94a3b8_1px,transparent_1px),linear-gradient(90deg,#94a3b8_1px,transparent_1px)] [background-size:32px_32px]"
        />
        <div className="relative flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-lg shadow-primary/30">
            <Activity className="size-5" />
          </div>
          <div>
            <p className="text-lg font-semibold tracking-tight">InfraWatch</p>
            <p className="text-xs text-muted-foreground">Monitoramento de infraestrutura</p>
          </div>
        </div>
        <div className="relative max-w-md space-y-8">
          <h2 className="text-3xl font-semibold leading-tight tracking-tight">
            Observe a disponibilidade e o desempenho dos seus serviços em um só lugar.
          </h2>
          <ul className="space-y-4 text-sm text-muted-foreground">
            <li className="flex gap-3">
              <Radar className="mt-0.5 size-4 shrink-0 text-cyan-400" />
              Verificações HTTP reais, agendadas pelo servidor, com histórico persistido.
            </li>
            <li className="flex gap-3">
              <Timer className="mt-0.5 size-4 shrink-0 text-emerald-400" />
              Latência, transições de estado e disponibilidade calculadas a partir dos dados.
            </li>
            <li className="flex gap-3">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-blue-400" />
              Somente destinos autorizados: allowlist, validação de URL e proteção contra SSRF.
            </li>
          </ul>
        </div>
        <p className="relative text-xs text-muted-foreground">Fase 1 — Fundação e monitoramento funcional</p>
      </aside>
      <main className="relative flex items-center justify-center p-6 sm:p-10">
        <div className="absolute top-4 right-4">
          <ThemeToggle />
        </div>
        <div className="w-full max-w-md">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
