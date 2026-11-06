import { format, formatDistanceToNowStrict, isValid, parseISO } from "date-fns";
import { ptBR } from "date-fns/locale";

export function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const d = parseISO(value);
  return isValid(d) ? d : null;
}

export function formatDateTime(value: string | null | undefined, pattern = "dd/MM/yyyy HH:mm:ss"): string {
  const d = parseDate(value);
  return d ? format(d, pattern, { locale: ptBR }) : "—";
}

export function formatTime(value: string | null | undefined): string {
  const d = parseDate(value);
  return d ? format(d, "HH:mm", { locale: ptBR }) : "—";
}

export function formatRelative(value: string | null | undefined): string {
  const d = parseDate(value);
  if (!d) return "—";
  return formatDistanceToNowStrict(d, { addSuffix: true, locale: ptBR });
}

export function formatMs(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  if (value >= 1000) return `${(value / 1000).toFixed(2).replace(".", ",")} s`;
  return `${Math.round(value)} ms`;
}

export function formatPercent(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined) return "—";
  return `${value.toFixed(digits).replace(".", ",")}%`;
}

export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds} s`;
  const m = Math.floor(seconds / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rm = m % 60;
  if (h < 24) return rm ? `${h} h ${rm} min` : `${h} h`;
  const d = Math.floor(h / 24);
  return `${d} d ${h % 24} h`;
}

export function formatInterval(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600} h`;
  if (seconds % 60 === 0) return `${seconds / 60} min`;
  return `${seconds} s`;
}

export function bucketLabel(iso: string, bucketSeconds: number): string {
  const d = parseDate(iso);
  if (!d) return "";
  return bucketSeconds >= 6 * 3600 ? format(d, "dd/MM HH:mm", { locale: ptBR }) : format(d, "HH:mm", { locale: ptBR });
}

export const ERROR_LABELS: Record<string, string> = {
  timeout: "Tempo limite",
  connect_timeout: "Tempo limite de conexão",
  connection_refused: "Conexão recusada",
  dns_failure: "Falha de DNS",
  tls_error: "Erro TLS",
  io_error: "Erro de rede",
  unexpected_status: "Código HTTP inesperado",
  unexpected_redirect: "Redirecionamento inesperado",
  too_many_redirects: "Muitos redirecionamentos",
  target_not_allowlisted: "Destino não autorizado",
  target_private_address: "Endereço interno bloqueado",
  config_invalid: "Configuração inválida",
};

export function errorLabel(code: string | null | undefined): string {
  if (!code) return "—";
  return ERROR_LABELS[code] ?? code;
}

export function formatBytes(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let v = value;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i += 1;
  }
  return `${v.toFixed(i === 0 ? 0 : 1).replace(".", ",")} ${units[i]}`;
}

export function formatUptime(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) return "—";
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d} d ${h} h`;
  if (h > 0) return `${h} h ${m} min`;
  return `${m} min`;
}
