import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { STATUS_HEX } from "@/components/StatusBadge";
import { bucketLabel, formatDateTime, formatMs, formatPercent } from "@/lib/format";
import type { AvailabilitySeries, LatencyResponse, Overview, ResourceStatus } from "@/types/api";
import type { IncidentSeries } from "@/types/incidents";

const GRID = "color-mix(in oklab, var(--border) 80%, transparent)";
const AXIS = "var(--muted-foreground)";
const SERIES_COLORS = ["#3b82f6", "#06b6d4", "#10b981", "#f59e0b", "#8b5cf6", "#ec4899", "#f97316", "#14b8a6"];

const num = (v: unknown): number | null => (typeof v === "number" ? v : v == null || v === "" ? null : Number(v));
const str = (v: unknown): string => (v == null ? "" : String(v));

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

export function AvailabilityChart({ data }: { data: AvailabilitySeries }) {
  const points = data.points.map((p) => ({
    ...p,
    label: bucketLabel(p.bucket, data.bucketSeconds),
    rate: p.successRate,
  }));
  return (
    <ResponsiveContainer width="100%" height={240}>
      <AreaChart data={points} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <defs>
          <linearGradient id="availFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#10b981" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#10b981" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          minTickGap={24}
        />
        <YAxis domain={[0, 100]} stroke={AXIS} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} unit="%" />
        <Tooltip
          {...tooltipStyle}
          formatter={(value) => [formatPercent(num(value)), "Verificações OK"]}
          labelFormatter={(_, payload) =>
            formatDateTime(payload?.[0]?.payload?.bucket as string | undefined, "dd/MM HH:mm")
          }
        />
        <Area
          type="monotone"
          dataKey="rate"
          stroke="#10b981"
          strokeWidth={2}
          fill="url(#availFill)"
          connectNulls
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function LatencyChart({ data }: { data: LatencyResponse }) {
  const index = new Map<string, Record<string, number | string | null>>();
  for (const s of data.series) {
    for (const p of s.points) {
      const row = index.get(p.bucket) ?? { bucket: p.bucket, label: bucketLabel(p.bucket, data.bucketSeconds) };
      row[s.resourceId] = p.avgResponseMs;
      index.set(p.bucket, row);
    }
  }
  const rows = [...index.values()].sort((a, b) => String(a.bucket).localeCompare(String(b.bucket)));
  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={rows} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          minTickGap={24}
        />
        <YAxis
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => formatMs(v)}
          width={64}
        />
        <Tooltip
          {...tooltipStyle}
          formatter={(value, name) => [formatMs(num(value)), str(name)]}
          labelFormatter={(_, payload) =>
            formatDateTime(payload?.[0]?.payload?.bucket as string | undefined, "dd/MM HH:mm")
          }
        />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        {data.series.map((s, i) => (
          <Line
            key={s.resourceId}
            type="monotone"
            dataKey={s.resourceId}
            name={s.resourceName}
            stroke={SERIES_COLORS[i % SERIES_COLORS.length]}
            strokeWidth={2}
            dot={false}
            connectNulls
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}

export function StatusDonut({ data }: { data: Overview["statusDistribution"] }) {
  const rows = data.filter((d) => d.total > 0);
  const total = rows.reduce((acc, r) => acc + r.total, 0);
  return (
    <div className="flex items-center gap-4">
      <div className="relative size-[150px] shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={rows}
              dataKey="total"
              nameKey="label"
              innerRadius={48}
              outerRadius={70}
              paddingAngle={2}
              stroke="none"
              isAnimationActive={false}
            >
              {rows.map((r) => (
                <Cell key={r.status} fill={STATUS_HEX[r.status as ResourceStatus]} />
              ))}
            </Pie>
            <Tooltip {...tooltipStyle} formatter={(value, name) => [num(value) ?? 0, str(name)]} />
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-2xl font-semibold tabular-nums">{total}</span>
          <span className="text-[11px] text-muted-foreground">recursos</span>
        </div>
      </div>
      <ul className="flex-1 space-y-1.5 text-sm">
        {rows.map((r) => (
          <li key={r.status} className="flex items-center gap-2">
            <span className="status-dot" style={{ background: STATUS_HEX[r.status as ResourceStatus] }} aria-hidden />
            <span className="flex-1 truncate text-muted-foreground">{r.label}</span>
            <span className="font-medium tabular-nums">{r.total}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function TypeBars({ data }: { data: Overview["resourcesByType"] }) {
  const max = Math.max(1, ...data.map((d) => d.total));
  const total = data.reduce((acc, d) => acc + d.total, 0);
  return (
    <ul className="space-y-3">
      {data.map((d, i) => (
        <li key={d.type}>
          <div className="mb-1 flex items-center justify-between gap-2 text-sm">
            <span className="truncate text-muted-foreground">{d.label}</span>
            <span className="font-medium tabular-nums">
              {d.total}
              <span className="ml-1 text-xs font-normal text-muted-foreground">
                ({total ? Math.round((d.total * 100) / total) : 0}%)
              </span>
            </span>
          </div>
          <div
            className="h-2 w-full overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-valuenow={d.total}
            aria-valuemin={0}
            aria-valuemax={max}
            aria-label={d.label}
          >
            <div
              className="h-full rounded-full"
              style={{ width: `${(d.total / max) * 100}%`, background: SERIES_COLORS[i % SERIES_COLORS.length] }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}

export function ResourceLatencyChart({
  points,
  bucketSeconds,
  degradedMs,
}: {
  points: { bucket: string; avgResponseMs: number | null; maxResponseMs: number | null; successRate: number | null }[];
  bucketSeconds: number;
  degradedMs?: number | null;
}) {
  const rows = points.map((p) => ({ ...p, label: bucketLabel(p.bucket, bucketSeconds) }));
  return (
    <ResponsiveContainer width="100%" height={260}>
      <AreaChart data={rows} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
        <defs>
          <linearGradient id="latFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          minTickGap={24}
        />
        <YAxis
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => formatMs(v)}
          width={64}
        />
        <Tooltip
          {...tooltipStyle}
          formatter={(value, name) => [formatMs(num(value)), name === "maxResponseMs" ? "Pico" : "Média"]}
          labelFormatter={(_, payload) =>
            formatDateTime(payload?.[0]?.payload?.bucket as string | undefined, "dd/MM HH:mm")
          }
        />
        <Area
          type="monotone"
          dataKey="maxResponseMs"
          stroke="#06b6d4"
          strokeWidth={1}
          strokeDasharray="4 3"
          fill="none"
          connectNulls
          isAnimationActive={false}
        />
        <Area
          type="monotone"
          dataKey="avgResponseMs"
          stroke="#3b82f6"
          strokeWidth={2}
          fill="url(#latFill)"
          connectNulls
          isAnimationActive={false}
        />
        {degradedMs ? (
          <Line
            type="monotone"
            dataKey={() => degradedMs}
            stroke="#f59e0b"
            strokeDasharray="6 4"
            dot={false}
            isAnimationActive={false}
            name="Limite de degradação"
          />
        ) : null}
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function IncidentsChart({ data }: { data: IncidentSeries }) {
  const rows = data.points.map((p) => ({
    ...p,
    label: bucketLabel(p.bucket, data.bucketSeconds),
    manual: p.total - p.automatic,
  }));
  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={rows} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={AXIS}
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          minTickGap={24}
        />
        <YAxis allowDecimals={false} stroke={AXIS} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
        <Tooltip
          {...tooltipStyle}
          formatter={(value, name) => [num(value) ?? 0, name === "automatic" ? "Automáticos" : "Manuais"]}
          labelFormatter={(_, payload) =>
            formatDateTime(payload?.[0]?.payload?.bucket as string | undefined, "dd/MM HH:mm")
          }
        />
        <Legend wrapperStyle={{ fontSize: 12 }} formatter={(v) => (v === "automatic" ? "Automáticos" : "Manuais")} />
        <Bar dataKey="automatic" stackId="a" fill="#ef4444" radius={[0, 0, 0, 0]} isAnimationActive={false} />
        <Bar dataKey="manual" stackId="a" fill="#8b5cf6" radius={[4, 4, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  );
}
