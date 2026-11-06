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
import type {
  AlertEvent,
  AlertRule,
  AlertRuleInput,
  AlertRuleOptions,
  AlertSummary,
  AppNotification,
} from "@/types/alerts";
import type {
  Incident,
  IncidentDetail,
  IncidentInput,
  IncidentPatch,
  IncidentSeries,
  IncidentStatus,
  IncidentSummary,
  MaintenanceInput,
  MaintenanceWindow,
  TimelineItem,
} from "@/types/incidents";

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
  overview: (period: Period, maintenance: "excluded" | "counted" = "excluded") =>
    api.get<Overview>("/dashboard/overview", { period, maintenance }),
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

export interface AlertListParams {
  status?: string;
  active?: boolean;
  severity?: string;
  metric?: string;
  resourceId?: string;
  page?: number;
  size?: number;
}

export const alertsApi = {
  list: (params: AlertListParams) =>
    api.get<PageResponse<AlertEvent>>("/alerts", params as Record<string, string | number | boolean | undefined>),
  get: (id: string) => api.get<AlertEvent>(`/alerts/${id}`),
  summary: () => api.get<AlertSummary>("/alerts/summary"),
  acknowledge: (id: string, note?: string) => api.post<AlertEvent>(`/alerts/${id}/acknowledge`, { note: note || null }),
  resolve: (id: string, note?: string) => api.post<AlertEvent>(`/alerts/${id}/resolve`, { note: note || null }),
  forResource: (resourceId: string) => api.get<AlertEvent[]>(`/resources/${resourceId}/alerts`),
  rules: () => api.get<AlertRule[]>("/alert-rules"),
  ruleOptions: () => api.get<AlertRuleOptions>("/alert-rules/options"),
  createRule: (data: AlertRuleInput) => api.post<AlertRule>("/alert-rules", data),
  updateRule: (id: string, data: Partial<AlertRuleInput>) => api.patch<AlertRule>(`/alert-rules/${id}`, data),
  deleteRule: (id: string) => api.delete<void>(`/alert-rules/${id}`),
};

export const notificationsApi = {
  list: (unreadOnly = false, page = 0, size = 20) =>
    api.get<PageResponse<AppNotification>>("/notifications", { unreadOnly, page, size }),
  unreadCount: () => api.get<{ unread: number }>("/notifications/unread-count"),
  markRead: (id: string) => api.post<AppNotification>(`/notifications/${id}/read`),
  markAllRead: () => api.post<{ updated: number }>("/notifications/read-all"),
};

export interface IncidentListParams {
  status?: string;
  active?: boolean;
  severity?: string;
  origin?: string;
  resourceId?: string;
  assigneeId?: string;
  page?: number;
  size?: number;
}

export const incidentsApi = {
  list: (params: IncidentListParams) =>
    api.get<PageResponse<Incident>>("/incidents", params as Record<string, string | number | boolean | undefined>),
  get: (id: string) => api.get<IncidentDetail>(`/incidents/${id}`),
  summary: () => api.get<IncidentSummary>("/incidents/summary"),
  create: (data: IncidentInput) => api.post<Incident>("/incidents", data),
  patch: (id: string, data: IncidentPatch) => api.patch<Incident>(`/incidents/${id}`, data),
  changeStatus: (id: string, status: IncidentStatus, note?: string) =>
    api.post<Incident>(`/incidents/${id}/status`, { status, note: note || null }),
  comment: (id: string, body: string) => api.post<TimelineItem>(`/incidents/${id}/comments`, { body }),
  forResource: (resourceId: string) => api.get<Incident[]>(`/resources/${resourceId}/incidents`),
  series: (period: Period) => api.get<IncidentSeries>("/dashboard/incidents", { period }),
};

export const maintenancesApi = {
  list: (upcomingOnly = false) => api.get<MaintenanceWindow[]>("/maintenances", { upcomingOnly }),
  get: (id: string) => api.get<MaintenanceWindow>(`/maintenances/${id}`),
  create: (data: MaintenanceInput) => api.post<MaintenanceWindow>("/maintenances", data),
  patch: (id: string, data: Partial<MaintenanceInput>) => api.patch<MaintenanceWindow>(`/maintenances/${id}`, data),
  finish: (id: string) => api.post<MaintenanceWindow>(`/maintenances/${id}/finish`),
  forResource: (resourceId: string) => api.get<MaintenanceWindow[]>(`/resources/${resourceId}/maintenances`),
};
