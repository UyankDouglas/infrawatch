import { useSearchParams } from "react-router";
import type { Period } from "@/types/api";

export const PERIODS: { value: Period; label: string }[] = [
  { value: "1h", label: "Última hora" },
  { value: "6h", label: "Últimas 6 horas" },
  { value: "24h", label: "Últimas 24 horas" },
  { value: "7d", label: "Últimos 7 dias" },
  { value: "30d", label: "Últimos 30 dias" },
];

export function usePeriod(defaultPeriod: Period = "24h"): [Period, (p: Period) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get("period");
  const period = PERIODS.some((p) => p.value === raw) ? (raw as Period) : defaultPeriod;
  const setPeriod = (p: Period) => {
    const next = new URLSearchParams(params);
    next.set("period", p);
    setParams(next, { replace: true });
  };
  return [period, setPeriod];
}
