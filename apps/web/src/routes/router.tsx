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
          {
            path: "/alertas",
            element: (
              <PlaceholderPage
                title="Alertas"
                phase="Fase 3"
                description="Regras de alerta, severidades e reconhecimento chegam na Fase 3 (tempo real e alertas)."
              />
            ),
          },
          {
            path: "/incidentes",
            element: (
              <PlaceholderPage
                title="Incidentes"
                phase="Fase 4"
                description="Abertura automática, timeline, comentários e resolução de incidentes chegam na Fase 4."
              />
            ),
          },
          {
            path: "/manutencoes",
            element: (
              <PlaceholderPage
                title="Manutenções"
                phase="Fase 4"
                description="Janelas de manutenção programada chegam na Fase 4."
              />
            ),
          },
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
