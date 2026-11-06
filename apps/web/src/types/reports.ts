import type { Environment, MemberRole, ResourceStatus, ResourceType } from "./api";
import type { AlertSeverity as Severity } from "./alerts";

export type MaintenanceMode = "excluded" | "counted";

export interface ResourceAvailabilityRow {
  resourceId: string;
  name: string;
  type: ResourceType;
  typeLabel: string;
  environment: Environment;
  environmentLabel: string;
  groupName: string | null;
  currentStatus: ResourceStatus;
  currentStatusLabel: string;
  availabilityPercent: number | null;
  observedSeconds: number;
  availableSeconds: number;
  unavailableSeconds: number;
  maintenanceSeconds: number;
  checks: number;
  failedChecks: number;
  avgResponseMs: number | null;
  maxResponseMs: number | null;
  p95ResponseMs: number | null;
  incidents: number;
  mttrSeconds: number | null;
  mtbfSeconds: number | null;
  mtbfNote: string | null;
}

export interface AvailabilityReport {
  from: string;
  to: string;
  period: string;
  maintenanceMode: MaintenanceMode;
  formula: string;
  rows: ResourceAvailabilityRow[];
  overallAvailabilityPercent: number | null;
  totalIncidents: number;
  overallMttrSeconds: number | null;
  totalUnavailableSeconds: number;
}

export interface IncidentReportRow {
  id: string;
  number: number;
  title: string;
  severity: Severity;
  severityLabel: string;
  status: string;
  statusLabel: string;
  origin: string;
  originLabel: string;
  resourceId: string | null;
  resourceName: string | null;
  assignee: string | null;
  openedAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  durationSeconds: number | null;
  timeToAcknowledgeSeconds: number | null;
  cause: string | null;
}

export interface IncidentsReport {
  from: string;
  to: string;
  period: string;
  rows: IncidentReportRow[];
  total: number;
  automatic: number;
  manual: number;
  bySeverityCritical: number;
  bySeverityHigh: number;
  bySeverityWarning: number;
  bySeverityInfo: number;
  mttrSeconds: number | null;
  mttaSeconds: number | null;
  note: string;
}

export interface MetricTrendPoint {
  bucket: string;
  cpuAvg: number | null;
  cpuMax: number | null;
  memAvg: number | null;
  memMax: number | null;
  diskAvg: number | null;
  diskMax: number | null;
}

export interface LatencyPoint {
  bucket: string;
  avgResponseMs: number | null;
  maxResponseMs: number | null;
  total: number;
  failures: number;
}

export interface ResourceReport {
  summary: ResourceAvailabilityRow | null;
  incidents: IncidentReportRow[];
  metricTrend: MetricTrendPoint[];
  metricGranularity: string;
  latency: LatencyPoint[];
}

export type ExportType = "AVAILABILITY" | "INCIDENTS" | "CHECKS";
export type ExportFormat = "CSV" | "XLSX";

export interface ReportExport {
  id: string;
  type: ExportType;
  typeLabel: string;
  format: ExportFormat;
  period: string;
  maintenanceMode: MaintenanceMode;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  rowCount: number;
  createdBy: string | null;
  createdAt: string;
}

// ---- Integrações -----------------------------------------------------------

export type ChannelType = "EMAIL" | "WEBHOOK";
export type DeliveryStatus = "PENDING" | "SENT" | "FAILED" | "SKIPPED" | "EXPIRED";

export interface NotificationChannel {
  id: string;
  name: string;
  type: ChannelType;
  enabled: boolean;
  emailRecipients: string[];
  webhookUrl: string | null;
  hasSecret: boolean;
  minSeverity: Severity;
  minSeverityLabel: string;
  eventTypes: string[];
  lastDeliveryAt: string | null;
  lastError: string | null;
  createdAt: string;
  dispatchEnabled: boolean;
}

export interface ChannelInput {
  name: string;
  type: ChannelType;
  enabled?: boolean;
  emailRecipients?: string[];
  webhookUrl?: string;
  webhookSecret?: string;
  minSeverity?: Severity;
  eventTypes?: string[];
}

export interface Delivery {
  id: string;
  channelId: string;
  channelName: string;
  eventType: string;
  severity: Severity;
  title: string;
  status: DeliveryStatus;
  attempts: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  sentAt: string | null;
  responseCode: number | null;
  createdAt: string;
}

export interface ChannelOptions {
  eventTypes: { value: string; label: string }[];
}

// ---- Membros e convites -----------------------------------------------------

export type InviteStatus = "PENDING" | "ACCEPTED" | "REVOKED" | "EXPIRED";

export interface Invite {
  id: string;
  email: string;
  role: MemberRole;
  roleLabel: string;
  status: InviteStatus;
  expiresAt: string;
  invitedBy: string | null;
  createdAt: string;
}

export interface InviteCreated {
  invite: Invite;
  token: string;
  link: string;
  message: string;
}

export interface InvitePublic {
  organizationName: string | null;
  email: string | null;
  role: MemberRole | null;
  roleLabel: string | null;
  valid: boolean;
  reason: string | null;
}
