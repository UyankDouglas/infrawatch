import type { AlertSeverity } from "./alerts";

export type IncidentStatus = "OPEN" | "ACKNOWLEDGED" | "INVESTIGATING" | "RESOLVED" | "CLOSED";
export type IncidentOrigin = "AUTOMATIC" | "MANUAL";

export interface UserRef {
  id: string;
  name: string;
}

export interface Incident {
  id: string;
  number: number;
  title: string;
  description: string | null;
  severity: AlertSeverity;
  severityLabel: string;
  status: IncidentStatus;
  statusLabel: string;
  origin: IncidentOrigin;
  originLabel: string;
  resourceId: string | null;
  resourceName: string | null;
  alertEventId: string | null;
  assignee: UserRef | null;
  createdBy: UserRef | null;
  openedAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  durationSeconds: number | null;
  cause: string | null;
  notes: string | null;
  commentCount: number;
  updatedAt: string;
}

export interface TimelineItem {
  kind: "history" | "comment" | "resource";
  at: string;
  user: UserRef | null;
  eventType: string;
  fromValue: string | null;
  toValue: string | null;
  text: string | null;
}

export interface IncidentDetail {
  incident: Incident;
  timeline: TimelineItem[];
  allowedTransitions: IncidentStatus[];
}

export interface IncidentSummary {
  open: number;
  acknowledged: number;
  investigating: number;
  resolvedLast24h: number;
  critical: number;
  high: number;
  mttrSeconds: number | null;
  mttrNote: string;
}

export interface IncidentInput {
  title: string;
  description?: string | null;
  severity: AlertSeverity;
  resourceId?: string | null;
  assigneeId?: string | null;
}

export interface IncidentPatch {
  title?: string;
  description?: string | null;
  severity?: AlertSeverity;
  assigneeId?: string | null;
  clearAssignee?: boolean;
  cause?: string | null;
  notes?: string | null;
}

export type MaintenanceStatus = "SCHEDULED" | "ACTIVE" | "COMPLETED" | "CANCELLED";

export interface MaintenanceWindow {
  id: string;
  title: string;
  reason: string | null;
  startsAt: string;
  endsAt: string;
  status: MaintenanceStatus;
  statusLabel: string;
  resources: { id: string; name: string }[];
  responsible: UserRef | null;
  createdBy: UserRef | null;
  activatedAt: string | null;
  finishedAt: string | null;
  finishReason: "ENDED_ON_SCHEDULE" | "ENDED_EARLY" | "CANCELLED" | null;
  durationSeconds: number | null;
  createdAt: string;
}

export interface MaintenanceInput {
  title: string;
  reason?: string | null;
  startsAt: string;
  endsAt: string;
  resourceIds: string[];
  responsibleId?: string | null;
}

export interface IncidentSeries {
  from: string;
  to: string;
  bucketSeconds: number;
  points: { bucket: string; total: number; automatic: number }[];
}
