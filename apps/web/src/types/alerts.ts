export type AlertMetric = "STATUS_DOWN" | "STATUS_DEGRADED" | "LATENCY" | "CPU" | "MEMORY" | "DISK" | "AGENT_NO_DATA";
export type AlertOperator = "GT" | "GTE" | "LT" | "LTE";
export type AlertSeverity = "INFO" | "WARNING" | "HIGH" | "CRITICAL";
export type AlertStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED";
export type AlertResolution = "CONDITION_CLEARED" | "MANUAL" | "RULE_DISABLED" | "RESOURCE_REMOVED";

export interface AlertRule {
  id: string;
  name: string;
  description: string | null;
  metric: AlertMetric;
  metricLabel: string;
  operator: AlertOperator | null;
  threshold: number | null;
  unit: string;
  durationSeconds: number;
  occurrences: number;
  severity: AlertSeverity;
  severityLabel: string;
  resourceId: string | null;
  resourceName: string | null;
  groupId: string | null;
  groupName: string | null;
  enabled: boolean;
  activeAlerts: number;
  createdAt: string;
  updatedAt: string;
}

export interface AlertRuleInput {
  name: string;
  description?: string | null;
  metric: AlertMetric;
  operator?: AlertOperator | null;
  threshold?: number | null;
  durationSeconds?: number;
  occurrences?: number;
  severity: AlertSeverity;
  resourceId?: string | null;
  groupId?: string | null;
  clearScope?: boolean;
  enabled?: boolean;
}

export interface AlertEvent {
  id: string;
  ruleId: string | null;
  ruleName: string | null;
  resourceId: string | null;
  resourceName: string | null;
  metric: AlertMetric;
  metricLabel: string;
  severity: AlertSeverity;
  severityLabel: string;
  status: AlertStatus;
  statusLabel: string;
  title: string;
  message: string | null;
  value: number | null;
  threshold: number | null;
  unit: string;
  triggeredAt: string;
  lastEvaluatedAt: string;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
  acknowledgedByName: string | null;
  acknowledgeNote: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
  resolvedByName: string | null;
  resolution: AlertResolution | null;
  resolutionNote: string | null;
  occurrenceCount: number;
  notificationCount: number;
  durationSeconds: number | null;
}

export interface AlertSummary {
  open: number;
  acknowledged: number;
  critical: number;
  high: number;
  warning: number;
  info: number;
}

export interface AlertRuleOptions {
  metrics: { value: AlertMetric; label: string; numeric: boolean; unit: string }[];
  operators: { value: AlertOperator; label: string }[];
  severities: { value: AlertSeverity; label: string }[];
}

export type NotificationType =
  | "RESOURCE_DOWN"
  | "RESOURCE_RECOVERED"
  | "RESOURCE_NO_DATA"
  | "ALERT_TRIGGERED"
  | "ALERT_RESOLVED"
  | "INCIDENT_CREATED"
  | "INCIDENT_ASSIGNED"
  | "MAINTENANCE_SCHEDULED";

export interface AppNotification {
  id: string;
  type: NotificationType;
  typeLabel: string;
  severity: AlertSeverity;
  severityLabel: string;
  title: string;
  message: string | null;
  link: string | null;
  resourceId: string | null;
  alertEventId: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface RealtimeEvent<T = unknown> {
  id: string;
  seq: number;
  type: string;
  organizationId: string;
  occurredAt: string;
  payload: T;
}
