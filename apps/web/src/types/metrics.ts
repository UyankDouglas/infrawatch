export type AgentStatus = "ACTIVE" | "REVOKED";

export interface AgentInfo {
  id: string;
  resourceId: string;
  resourceName: string;
  name: string;
  status: AgentStatus;
  intervalSeconds: number;
  hostname: string | null;
  agentVersion: string | null;
  lastSeenAt: string | null;
  lastSampleAt: string | null;
  stale: boolean;
  tokenPrefix: string | null;
  createdAt: string;
}

export interface AgentTokenResponse {
  agent: AgentInfo;
  token: string;
  message: string;
}

export interface MetricValue {
  key: "cpu" | "memory" | "disk" | "uptime" | "load" | string;
  label: string;
  value: number | null;
  unit: string;
  available: boolean;
  note: string | null;
}

export interface LatestSample {
  sampledAt: string;
  receivedAt: string;
  cpuPercent: number | null;
  loadAvg1m: number | null;
  memTotalBytes: number | null;
  memUsedBytes: number | null;
  memPercent: number | null;
  diskPath: string | null;
  diskTotalBytes: number | null;
  diskUsedBytes: number | null;
  diskPercent: number | null;
  uptimeSeconds: number | null;
}

export interface MetricSeriesPoint {
  bucket: string;
  samples: number;
  cpuAvg: number | null;
  cpuMax: number | null;
  memPercentAvg: number | null;
  memPercentMax: number | null;
  memUsedAvg: number | null;
  diskPercentAvg: number | null;
  diskPercentMax: number | null;
  diskUsedAvg: number | null;
}

export interface AgentMetrics {
  resourceId: string;
  source: "agent";
  hasAgent: boolean;
  agent: AgentInfo | null;
  stale: boolean;
  from: string;
  to: string;
  bucketSeconds: number;
  granularity: "none" | "raw" | "raw_fallback" | "rollup_5m" | "rollup_1h";
  latest: LatestSample | null;
  values: MetricValue[];
  missingMetrics: string[];
  series: MetricSeriesPoint[];
  note: string;
}
