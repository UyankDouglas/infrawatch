import { Navigate, Outlet, useLocation } from "react-router";
import { useAuth } from "./AuthProvider";
import { FullPageLoader } from "@/components/FullPageLoader";

export function RequireAuth() {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <FullPageLoader />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

export function RedirectIfAuthenticated() {
  const { user, isLoading } = useAuth();
  if (isLoading) return <FullPageLoader />;
  if (user) return <Navigate to="/" replace />;
  return <Outlet />;
}
