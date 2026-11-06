# Observabilidade do InfraWatch

Dois conjuntos de dados **distintos**:

| | Métricas dos recursos monitorados | Métricas do próprio InfraWatch |
|---|---|---|
| O que são | status, latência, códigos HTTP, (Fase 2) CPU/RAM/disco | saúde da JVM, HTTP da API, desempenho do motor |
| Onde vivem | tabelas de domínio (`health_check_executions`, …) e API `/api/v1` | Actuator / Micrometer na porta **8081** |
| Quem consome | usuários do painel | operador do InfraWatch, Prometheus (Fase 5) |

## Actuator (porta 8081)

- `GET /actuator/health` (+ `/liveness`, `/readiness`), `/info`, `/metrics`, `/prometheus` (quando o registry estiver presente, Fase 5).
- Publicada somente em `127.0.0.1` no Compose. Nunca exposta na porta pública 8080.

## Micrometer

Além das métricas padrão (JVM, Tomcat, HikariCP, `http.server.requests`), o motor publica:

| Métrica | Tipo | Tags |
|---|---|---|
| `infrawatch.engine.checks.dispatched` | counter | — |
| `infrawatch.engine.checks.executed` | counter | `result`, `error` |
| `infrawatch.engine.checks.duration` | timer | `result` |
| `infrawatch.engine.checks.inflight` | gauge | — |
| `infrawatch.engine.status.transitions` | counter | — |
| `infrawatch.engine.resources.stale` | counter | — |
| `infrawatch.engine.agent.samples.accepted` / `.rejected` | counter | — |
| `infrawatch.engine.alerts.triggered` / `.resolved` | counter | — |
| `infrawatch.engine.realtime.events` | counter | — |

## Logs

- `CorrelationIdFilter`: `X-Request-Id` aceito ou gerado, devolvido na resposta e colocado no MDC (`correlationId`).
- Cada verificação carrega `runId` no MDC.
- Perfil `docker`: `logging.structured.format.console=ecs` (JSON compatível com Elastic/Loki).
- Transições de estado e resultados atrasados são logados em `INFO`; recursos marcados como Sem dados em `WARN`.

## Tracing

OpenTelemetry (Micrometer Tracing + OTLP) será ativado na Fase 5 com um Collector opcional no Compose. As dependências já estão gerenciadas pelo BOM do Spring Boot 4.1.
