import { createBrowserRouter } from "react-router";
import { RedirectIfAuthenticated, RequireAuth } from "@/features/auth/RequireAuth";
import { AppLayout } from "@/layouts/AppLayout";
import { AuthLayout } from "@/layouts/AuthLayout";
import { LoginPage } from "@/pages/LoginPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { OverviewPage } from "@/pages/OverviewPage";
import { ResourcesPage } from "@/pages/ResourcesPage";
import { ResourceDetailPage } from "@/pages/ResourceDetailPage";
import { SettingsPage } from "@/pages/SettingsPage";
import { PlaceholderPage } from "@/pages/PlaceholderPage";
import { AlertsPage } from "@/pages/AlertsPage";
import { IncidentsPage } from "@/pages/IncidentsPage";
import { IncidentDetailPage } from "@/pages/IncidentDetailPage";
import { MaintenancesPage } from "@/pages/MaintenancesPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

export const router = createBrowserRouter([
  {
    element: <RedirectIfAuthenticated />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: "/login", element: <LoginPage /> },
          { path: "/cadastro", element: <RegisterPage /> },
        ],
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: "/", element: <OverviewPage /> },
          { path: "/recursos", element: <ResourcesPage /> },
          { path: "/recursos/:id", element: <ResourceDetailPage /> },
          { path: "/alertas", element: <AlertsPage /> },
          { path: "/incidentes", element: <IncidentsPage /> },
          { path: "/incidentes/:id", element: <IncidentDetailPage /> },
          { path: "/manutencoes", element: <MaintenancesPage /> },
          {
            path: "/relatorios",
            element: (
              <PlaceholderPage
                title="Relatórios"
                phase="Fase 5"
                description="Disponibilidade por período, MTTR, MTBF e exportações chegam na Fase 5."
              />
            ),
          },
          { path: "/configuracoes", element: <SettingsPage /> },
          { path: "*", element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
