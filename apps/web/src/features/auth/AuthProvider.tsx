import { createContext, useCallback, useContext, useMemo, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/services/api";
import { authApi } from "@/services/endpoints";
import type { Me, MemberRole } from "@/types/api";

interface AuthContextValue {
  user: Me | null;
  isLoading: boolean;
  role: MemberRole | null;
  hasRole: (minimum: MemberRole) => boolean;
  refresh: () => Promise<void>;
  setUser: (me: Me | null) => void;
  logout: () => Promise<void>;
}

const ROLE_LEVEL: Record<MemberRole, number> = { VIEWER: 1, OPERATOR: 2, ADMIN: 3 };
const AuthContext = createContext<AuthContextValue | null>(null);
export const ME_QUERY_KEY = ["auth", "me"] as const;

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: async () => {
      try {
        return await authApi.me();
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 60_000,
    retry: false,
  });

  const setUser = useCallback(
    (me: Me | null) => {
      queryClient.setQueryData(ME_QUERY_KEY, me);
      if (!me) queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== "auth" });
    },
    [queryClient],
  );

  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
      queryClient.clear();
    }
  }, [queryClient, setUser]);

  const user = query.data ?? null;
  const role = user?.currentOrganization?.role ?? null;

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading: query.isLoading,
      role,
      hasRole: (minimum) => (role ? ROLE_LEVEL[role] >= ROLE_LEVEL[minimum] : false),
      refresh,
      setUser,
      logout,
    }),
    [user, query.isLoading, role, refresh, setUser, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth deve ser usado dentro de AuthProvider");
  return ctx;
}
