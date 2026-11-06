# Observabilidade do InfraWatch

Dois conjuntos de dados **distintos**:

| | Métricas dos recursos monitorados | Métricas do próprio InfraWatch |
|---|---|---|
| O que são | status, latência, códigos HTTP, (Fase 2) CPU/RAM/disco | saúde da JVM, HTTP da API, desempenho do motor |
| Onde vivem | tabelas de domínio (`health_check_executions`, …) e API `/api/v1` | Actuator / Micrometer na porta **8081** |
| Quem consome | usuários do painel | operador do InfraWatch, Prometheus/Grafana (perfil `observability`) |

## Actuator (porta 8081)

- `GET /actuator/health` (+ `/liveness`, `/readiness`), `/info`, `/metrics`, `/prometheus` (formato Prometheus, `micrometer-registry-prometheus`). O health check de e-mail está desligado (`management.health.mail.enabled=false`): SMTP é opcional e não deve derrubar a API.
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
| `infrawatch.engine.deliveries.sent` / `.failed` | counter | — |

Histogramas (`percentiles-histogram`) ativados para `http.server.requests` e `infrawatch.engine.checks.duration`, permitindo `histogram_quantile` no Prometheus. Tag comum `application=infrawatch-api`.

## Prometheus + Grafana (perfil `observability`)

```bash
docker compose --profile full --profile observability up -d
```

| Serviço | URL | Observação |
|---|---|---|
| Prometheus | http://127.0.0.1:9090 | `docker/prometheus/prometheus.yml` coleta `api:8081/actuator/prometheus` a cada 15 s (e `host.docker.internal:8081` quando a API roda fora do Docker); retenção 7 d |
| Grafana | http://127.0.0.1:3000 (`admin`/`admin`, configurável no `.env`) | datasource e dashboard **InfraWatch — Motor de monitoramento** provisionados em `docker/grafana/` (verificações/min e duração p50/p95, transições, alertas, amostras de agentes, entregas externas, HTTP por status e p95 por rota, heap, pool Hikari) |
| Mailpit | http://127.0.0.1:8025 (SMTP 1025) | servidor SMTP de teste para os canais de e-mail — não entrega para fora |
| OTel Collector | 127.0.0.1:4318 (OTLP/HTTP) | recebe traces e os imprime no próprio log (`docker/otel/collector.yaml`); troque o exporter para Jaeger/Tempo se quiser |

Todas as portas são publicadas apenas em `127.0.0.1`.

## Logs

- `CorrelationIdFilter`: `X-Request-Id` aceito ou gerado, devolvido na resposta e colocado no MDC (`correlationId`).
- Cada verificação carrega `runId` no MDC.
- Perfil `docker`: `logging.structured.format.console=ecs` (JSON compatível com Elastic/Loki).
- Transições de estado e resultados atrasados são logados em `INFO`; recursos marcados como Sem dados em `WARN`.

## Tracing

`spring-boot-starter-opentelemetry` (Micrometer Tracing + exportador OTLP) está no classpath, com amostragem **0 por padrão** (`INFRAWATCH_TRACING_SAMPLING=0.0`, isto é, desligado). Para ativar: `INFRAWATCH_TRACING_SAMPLING=0.1` (10 %) e `INFRAWATCH_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces` (padrão no Compose). `service.name=infrawatch-api`. O `traceId` passa a acompanhar os logs junto com o `correlationId`.
