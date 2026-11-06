import { useEffect, useState } from "react";
import { Outlet } from "react-router";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

const STORAGE_KEY = "infrawatch.sidebar.collapsed";

export function AppLayout() {
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try {
      return window.localStorage.getItem(STORAGE_KEY) === "1";
    } catch {
      return false;
    }
  });
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, collapsed ? "1" : "0");
    } catch {
      // ignorar
    }
  }, [collapsed]);

  return (
    <div className="flex min-h-screen bg-background">
      <a
        href="#conteudo"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:bg-primary focus:px-3 focus:py-2 focus:text-primary-foreground"
      >
        Ir para o conteúdo
      </a>
      <div className={cn("hidden shrink-0 transition-[width] duration-200 md:block", collapsed ? "w-[68px]" : "w-64")}>
        <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((c) => !c)} />
      </div>
      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="left" className="w-72 p-0">
          <SheetTitle className="sr-only">Navegação</SheetTitle>
          <Sidebar collapsed={false} onToggle={() => setMobileOpen(false)} mobile />
        </SheetContent>
      </Sheet>
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onOpenMobileMenu={() => setMobileOpen(true)} />
        <main id="conteudo" className="flex-1 p-4 sm:p-6 lg:p-8" tabIndex={-1}>
          <div className="mx-auto w-full max-w-[1400px] space-y-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
