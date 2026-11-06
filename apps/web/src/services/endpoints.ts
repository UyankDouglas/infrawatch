import { api } from "./api";
import type {
  AuditEntry,
  AvailabilitySeries,
  Execution,
  LatencyResponse,
  Me,
  Member,
  OrganizationSummary,
  Overview,
  PageResponse,
  Period,
  Resource,
  ResourceGroup,
  ResourceInput,
  ResourceMetrics,
  ResourceOptions,
  RunNowResponse,
  Transition,
} from "@/types/api";
import type { AgentInfo, AgentMetrics, AgentTokenResponse } from "@/types/metrics";

export const authApi = {
  me: () => api.get<Me>("/auth/me"),
  login: (email: string, password: string) => api.post<Me>("/auth/login", { email, password }),
  register: (data: { fullName: string; email: string; password: string; organizationName: string }) =>
    api.post<Me>("/auth/register", data),
  logout: () => api.post<void>("/auth/logout"),
};

export const organizationApi = {
  current: () => api.get<OrganizationSummary>("/organizations/current"),
  members: () => api.get<Member[]>("/organizations/current/members"),
  switch: (id: string) => api.post<OrganizationSummary>(`/organizations/${id}/switch`),
};

export interface ResourceListParams {
  search?: string;
  status?: string;
  type?: string;
  environment?: string;
  groupId?: string;
  enabled?: boolean;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "asc" | "desc";
}

export const resourcesApi = {
  list: (params: ResourceListParams, signal?: AbortSignal) =>
    api.get<PageResponse<Resource>>(
      "/resources",
      params as Record<string, string | number | boolean | undefined>,
      signal,
    ),
  get: (id: string) => api.get<Resource>(`/resources/${id}`),
  create: (data: ResourceInput) => api.post<Resource>("/resources", data),
  update: (id: string, data: Partial<ResourceInput>) => api.patch<Resource>(`/resources/${id}`, data),
  remove: (id: string) => api.delete<void>(`/resources/${id}`),
  options: () => api.get<ResourceOptions>("/resources/options"),
  checks: (id: string, page = 0, size = 25) =>
    api.get<PageResponse<Execution>>(`/resources/${id}/checks`, { page, size }),
  history: (id: string, page = 0, size = 25) =>
    api.get<PageResponse<Transition>>(`/resources/${id}/history`, { page, size }),
  metrics: (id: string, period: Period) => api.get<ResourceMetrics>(`/resources/${id}/metrics`, { period }),
  runNow: (id: string) => api.post<RunNowResponse>(`/resources/${id}/checks/run`),
};

export const groupsApi = {
  list: () => api.get<ResourceGroup[]>("/resource-groups"),
  create: (data: { name: string; description?: string }) => api.post<ResourceGroup>("/resource-groups", data),
  update: (id: string, data: { name: string; description?: string }) =>
    api.put<ResourceGroup>(`/resource-groups/${id}`, data),
  remove: (id: string) => api.delete<void>(`/resource-groups/${id}`),
};

export const dashboardApi = {
  overview: (period: Period) => api.get<Overview>("/dashboard/overview", { period }),
  availability: (period: Period) => api.get<AvailabilitySeries>("/dashboard/availability", { period }),
  latency: (period: Period, limit = 5) => api.get<LatencyResponse>("/dashboard/latency", { period, limit }),
};

export const auditApi = {
  list: (page = 0, size = 25) => api.get<PageResponse<AuditEntry>>("/audit", { page, size }),
};

export const agentsApi = {
  list: () => api.get<AgentInfo[]>("/agents"),
  forResource: (resourceId: string) => api.get<AgentInfo | undefined>(`/resources/${resourceId}/agent`),
  metrics: (resourceId: string, period: Period) =>
    api.get<AgentMetrics>(`/resources/${resourceId}/agent-metrics`, { period }),
  register: (data: { resourceId: string; name: string; intervalSeconds?: number }) =>
    api.post<AgentTokenResponse>("/agents/register", data),
  rotate: (agentId: string) => api.post<AgentTokenResponse>(`/agents/${agentId}/rotate`),
  updateInterval: (agentId: string, intervalSeconds: number) =>
    api.patch<AgentInfo>(`/agents/${agentId}`, { intervalSeconds }),
  revoke: (agentId: string) => api.delete<AgentInfo>(`/agents/${agentId}`),
};
