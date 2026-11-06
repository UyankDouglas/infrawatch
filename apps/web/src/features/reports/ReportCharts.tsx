import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatDateTime, formatMs, formatPercent } from "@/lib/format";
import type { IncidentsReport, LatencyPoint, MetricTrendPoint, ResourceAvailabilityRow } from "@/types/reports";

const GRID = "color-mix(in oklab, var(--border) 80%, transparent)";
const AXIS = "var(--muted-foreground)";
const num = (v: unknown): number | null => (typeof v === "number" ? v : v == null || v === "" ? null : Number(v));
const tooltipStyle = {
  contentStyle: {
    background: "var(--popover)",
    border: "1px solid var(--border)",
    borderRadius: 8,
    color: "var(--popover-foreground)",
    fontSize: 12,
  },
  labelStyle: { color: "var(--muted-foreground)" },
};

function colorFor(percent: number | null): string {
  if (percent == null) return "#94a3b8";
  if (percent >= 99.9) return "#10b981";
  if (percent >= 99) return "#3b82f6";
  if (percent >= 95) return "#f59e0b";
  return "#ef4444";
}

export function AvailabilityBars({ rows }: { rows: ResourceAvailabilityRow[] }) {
  const data = rows.map((r) => ({ name: r.name, percent: r.availabilityPercent }));
  const height = Math.max(160, Math.min(480, 28 * data.length + 40));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 4 }}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis
          type="number"
          domain={[0, 100]}
          unit="%"
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          type="category"
          dataKey="name"
          width={160}
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
        />
        <Tooltip
          {...tooltipStyle}
          formatter={(value) => [value == null ? "sem dados" : formatPercent(num(value)), "Disponibilidade"]}
        />
        <Bar dataKey="percent" radius={[0, 4, 4, 0]} isAnimationActive={false}>
          {data.map((d) => (
            <Cell key={d.name} fill={colorFor(d.percent)} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function IncidentsBySeverity({ report }: { report: IncidentsReport }) {
  const data = [
    { label: "Crítico", value: report.bySeverityCritical, color: "#ef4444" },
    { label: "Alta", value: report.bySeverityHigh, color: "#f97316" },
    { label: "Atenção", value: report.bySeverityWarning, color: "#f59e0b" },
    { label: "Informativo", value: report.bySeverityInfo, color: "#3b82f6" },
  ];
  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: -24, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="label" stroke={AXIS} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
        <YAxis allowDecimals={false} stroke={AXIS} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
        <Tooltip {...tooltipStyle} formatter={(value) => [String(value), "Incidentes"]} />
        <Bar dataKey="value" radius={[4, 4, 0, 0]} isAnimationActive={false}>
          {data.map((d) => (
            <Cell key={d.label} fill={d.color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function MetricTrendChart({ points }: { points: MetricTrendPoint[] }) {
  const data = points.map((p) => ({ ...p, label: formatDateTime(p.bucket, "dd/MM HH:mm") }));
  return (
    <ResponsiveContainer width="100%" height={240}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          minTickGap={32}
        />
        <YAxis domain={[0, 100]} unit="%" stroke={AXIS} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
        <Tooltip {...tooltipStyle} formatter={(value, name) => [formatPercent(num(value), 1), String(name)]} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Line
          type="monotone"
          dataKey="cpuAvg"
          name="CPU média"
          stroke="#3b82f6"
          dot={false}
          strokeWidth={2}
          connectNulls
          isAnimationActive={false}
        />
        <Line
          type="monotone"
          dataKey="memAvg"
          name="Memória média"
          stroke="#8b5cf6"
          dot={false}
          strokeWidth={2}
          connectNulls
          isAnimationActive={false}
        />
        <Line
          type="monotone"
          dataKey="diskAvg"
          name="Disco média"
          stroke="#f59e0b"
          dot={false}
          strokeWidth={2}
          connectNulls
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

export function LatencyTrendChart({ points }: { points: LatencyPoint[] }) {
  const data = points.map((p) => ({ ...p, label: formatDateTime(p.bucket, "dd/MM HH:mm") }));
  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          minTickGap={32}
        />
        <YAxis stroke={AXIS} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} unit=" ms" />
        <Tooltip {...tooltipStyle} formatter={(value, name) => [formatMs(num(value)), String(name)]} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Line
          type="monotone"
          dataKey="avgResponseMs"
          name="Média"
          stroke="#06b6d4"
          dot={false}
          strokeWidth={2}
          connectNulls
          isAnimationActive={false}
        />
        <Line
          type="monotone"
          dataKey="maxResponseMs"
          name="Máxima"
          stroke="#f97316"
          dot={false}
          strokeWidth={1.5}
          strokeDasharray="4 3"
          connectNulls
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
