# Histórico de versões

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). Datas em ISO 8601.

## [1.0.0] — 2026-11-06

Primeira versão completa: as seis fases do plano entregues.

### Fase 6 — Qualidade, segurança e apresentação
- Testes de concorrência (`ConcurrencyIT`), varredura de isolamento entre organizações (`IsolationSweepIT`) e
  cabeçalhos/erros (`SecurityHeadersIT`); benchmark reproduzível (`BenchmarkIT`, `docs/benchmark.md`).
- Correção de N+1 ao carregar recursos em lote (`@BatchSize`/`@EntityGraph`) encontrada pelo benchmark.
- Correção de reenvio duplicado no outbox com despachantes concorrentes (lease de 60 s) encontrada por `ConcurrencyIT`.
- CSP e cabeçalhos de segurança no nginx e na API; `npm audit` no CI; Dependabot; CodeQL.
- `docker-compose.prod.yml` revisado e perfil `tls` com Caddy.
- Capturas de tela reproduzíveis, README final, `docs/testing.md`, `docs/security-review.md`, CONTRIBUTING.

### Fase 5 — Relatórios, exportações, integrações e administração
- Relatórios de disponibilidade (modo de manutenção declarado), incidentes (MTTR, MTTA) e recurso; MTBF com limitação explícita; latência p95.
- Exportações CSV/XLSX com histórico, download e auditoria.
- Canais externos (e-mail SMTP e webhooks assinados HMAC-SHA256) via outbox com retentativas, deduplicação e anti-SSRF; envio desligado por padrão.
- Convites, perfis, remoção de membros, recuperação de senha, auditoria com filtros; Spring Session JDBC.
- `/actuator/prometheus`, OpenTelemetry opcional, perfil Compose `observability` (Prometheus, Grafana, Mailpit, OTel Collector).

### Fase 4 — Incidentes e manutenções
- Incidentes automáticos (vinculados ao alerta) e manuais com ciclo de vida, timeline, comentários e MTTR.
- Manutenções programadas com supressão de alertas/incidentes e disponibilidade com manutenção excluída/contada.

### Fase 3 — Tempo real e alertas
- WebSocket STOMP autenticado por sessão com isolamento por organização e ressincronização via REST.
- Regras de alerta por recurso/grupo/organização com severidade, ocorrências e histerese; notificações internas.

### Fase 2 — Métricas por agente autorizado
- Agente Java 21 sem dependências; tokens com prefixo e hash; ingestão autenticada; amostras brutas e rollups; agente silencioso → Sem dados.

### Fase 1 — Fundação e monitoramento HTTP
- Contas, organizações e perfis; recursos monitorados; motor de verificações com allowlist e proteção anti-SSRF;
  estados com histerese; histórico; dashboard; serviços de demonstração controláveis; Docker Compose; CI.
