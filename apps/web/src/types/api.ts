export type MemberRole = "ADMIN" | "OPERATOR" | "VIEWER";

export type ResourceStatus = "PENDING" | "UP" | "DOWN" | "DEGRADED" | "MAINTENANCE" | "UNKNOWN";
export type ResourceType = "HTTP" | "HTTPS" | "HEALTH_ENDPOINT" | "AGENT_SERVER";
export type Environment = "DEVELOPMENT" | "STAGING" | "PRODUCTION";

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface OrganizationSummary {
  id: string;
  name: string;
  slug: string | null;
  role: MemberRole;
  roleLabel: string;
}

export interface Me {
  id: string;
  email: string;
  fullName: string;
  createdAt: string;
  currentOrganization: OrganizationSummary | null;
  organizations: OrganizationSummary[];
}

export interface Member {
  userId: string;
  fullName: string;
  email: string;
  role: MemberRole;
  roleLabel: string;
  since: string;
}

export interface CheckConfig {
  intervalSeconds: number;
  timeoutMs: number;
  httpMethod: string;
  expectedStatusCodes: string;
  degradedLatencyMs: number | null;
  failureThreshold: number;
  recoveryThreshold: number;
  followRedirects: boolean;
}

export interface Resource {
  id: string;
  name: string;
  description: string | null;
  type: ResourceType;
  typeLabel: string;
  targetUrl: string;
  environment: Environment;
  environmentLabel: string;
  group: { id: string; name: string } | null;
  owner: string | null;
  tags: string[];
  enabled: boolean;
  status: ResourceStatus;
  statusLabel: string;
  statusSince: string;
  lastCheckAt: string | null;
  lastSuccess: boolean | null;
  lastResponseTimeMs: number | null;
  lastHttpStatus: number | null;
  lastErrorCode: string | null;
  consecutiveFailures: number;
  consecutiveSuccesses: number;
  nextCheckAt: string;
  check: CheckConfig | null;
  createdAt: string;
  updatedAt: string;
}

export interface ResourceGroup {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  resourceCount: number;
}

export interface CheckConfigInput {
  intervalSeconds?: number;
  timeoutMs?: number;
  httpMethod?: string;
  expectedStatusCodes?: string;
  degradedLatencyMs?: number | null;
  failureThreshold?: number;
  recoveryThreshold?: number;
  followRedirects?: boolean;
}

export interface ResourceInput {
  name: string;
  description?: string | null;
  type: ResourceType;
  targetUrl: string;
  environment: Environment;
  groupId?: string | null;
  clearGroup?: boolean;
  owner?: string | null;
  tags?: string[];
  enabled?: boolean;
  check?: CheckConfigInput;
}

export interface Execution {
  id: string;
  runId: string;
  checkType: string;
  startedAt: string;
  finishedAt: string;
  durationMs: number;
  success: boolean;
  httpStatus: number | null;
  responseTimeMs: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  resolvedAddress: string | null;
}

export interface Transition {
  id: string;
  fromStatus: ResourceStatus;
  fromLabel: string;
  toStatus: ResourceStatus;
  toLabel: string;
  reason: string | null;
  executionId: string | null;
  changedAt: string;
}

export interface LatencyPoint {
  bucket: string;
  total: number;
  successes: number;
  avgResponseMs: number | null;
  maxResponseMs: number | null;
  successRate: number | null;
}

export interface ResourceMetrics {
  resourceId: string;
  from: string;
  to: string;
  bucketSeconds: number;
  latency: LatencyPoint[];
  availabilityPercent: number | null;
  observedSeconds: number;
  availableSeconds: number;
  availabilityNote: string;
}

export interface RunNowResponse {
  executed: boolean;
  execution: Execution | null;
  previousStatus: ResourceStatus;
  currentStatus: ResourceStatus;
  transitioned: boolean;
  message: string;
}

export interface ResourceSummary {
  id: string;
  name: string;
  type: ResourceType;
  typeLabel: string;
  environment: Environment;
  environmentLabel: string;
  status: ResourceStatus;
  statusLabel: string;
  statusSince: string;
  lastCheckAt: string | null;
  lastResponseTimeMs: number | null;
  lastErrorCode: string | null;
  groupName: string | null;
}

export interface TransitionSummary {
  id: string;
  resourceId: string;
  resourceName: string;
  fromStatus: ResourceStatus;
  fromLabel: string;
  toStatus: ResourceStatus;
  toLabel: string;
  reason: string | null;
  changedAt: string;
}

export interface Overview {
  from: string;
  to: string;
  period: string;
  totalResources: number;
  available: number;
  unavailable: number;
  degraded: number;
  withoutData: number;
  pendingResources: number;
  unknownResources: number;
  pausedResources: number;
  activeIncidents: number;
  activeAlerts: number;
  availabilityPercent: number | null;
  observedSeconds: number;
  availabilityNote: string;
  averageResponseMs: number | null;
  checksInPeriod: number;
  failedChecksInPeriod: number;
  statusDistribution: { status: ResourceStatus; label: string; total: number }[];
  resourcesByType: { type: ResourceType; label: string; total: number }[];
  problematic: ResourceSummary[];
  resources: ResourceSummary[];
  recentTransitions: TransitionSummary[];
}

export interface AvailabilitySeries {
  from: string;
  to: string;
  bucketSeconds: number;
  points: { bucket: string; total: number; successes: number; successRate: number | null }[];
  note: string;
}

export interface LatencyResponse {
  from: string;
  to: string;
  bucketSeconds: number;
  series: {
    resourceId: string;
    resourceName: string;
    averageMs: number;
    points: { bucket: string; avgResponseMs: number | null; maxResponseMs: number | null }[];
  }[];
}

export interface AuditEntry {
  id: string;
  userId: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface ResourceOptions {
  types: { value: ResourceType; label: string; httpCheck: boolean }[];
  environments: { value: Environment; label: string }[];
  statuses: { value: ResourceStatus; label: string }[];
  intervals: number[];
}

export type Period = "1h" | "6h" | "24h" | "7d" | "30d";
