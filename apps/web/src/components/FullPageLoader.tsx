import { Activity } from "lucide-react";

export function FullPageLoader({ label = "Carregando InfraWatch…" }: { label?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background" role="status" aria-live="polite">
      <div className="flex flex-col items-center gap-3 text-muted-foreground">
        <Activity className="size-8 animate-pulse text-primary" aria-hidden />
        <span className="text-sm">{label}</span>
      </div>
    </div>
  );
}
