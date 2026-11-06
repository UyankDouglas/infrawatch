# PROGRESS.md — InfraWatch

> Arquivo de retomada. Se uma sessão for interrompida, comece lendo este arquivo.

## Fase atual

**Fase 1 — Fundação e monitoramento funcional: CONCLUÍDA (2026-11-06).**
**Fase 2 — Métricas de infraestrutura (agente autorizado): CONCLUÍDA (2026-11-06).**
**Fase 3 — Tempo real (WebSocket) e alertas: CONCLUÍDA (2026-11-06).**
**Fase 4 — Incidentes e manutenções programadas: CONCLUÍDA (2026-11-06).**
Próxima: **Fase 5 — Relatórios e administração**.

## Stack e versões escolhidas (verificadas no Maven Central / npm em 2026-11-06)

| Componente | Versão | Observação |
|---|---|---|
| Java | 21 LTS (Temurin 21.0.13) | |
| Spring Boot | 4.1.2 | starters modulares (`spring-boot-starter-webmvc`, `-flyway`, `-micrometer-metrics`), Jackson 3, Spring Security 7.1, Hibernate 7.4 |
| springdoc-openapi | 3.2.0 | compilado contra Boot 4.1.2 |
| Testcontainers | 2.0.5 (gerenciado pelo Boot) | artefato `testcontainers-postgresql`, pacote `org.testcontainers.postgresql` |
| PostgreSQL | 17 (alpine) | porta **5433** no host (5432 costuma estar ocupada) |
| Node / Vite / React | 22 / 8.3 / 19.3 | |
| Tailwind / shadcn | 4.3 / 4.23 (estilo new-york, Radix) | |
| TanStack Query / Table | 5.x / **8.x** (fixado; v9 ainda recente) | |
| React Router | 8.5 | API `createBrowserRouter` |
| Vitest / Playwright | 5.1 / 1.65 | |

## Funcionalidades concluídas (Fase 1)

- [x] Monorepositório (`apps/api`, `apps/web`, `apps/demo-services`, `e2e`, `docs`, `scripts`)
- [x] Docker Compose: PostgreSQL + 3 serviços de demonstração; perfil `full` sobe API + web (nginx)
- [x] Flyway `V1__fundacao.sql` (users, organizations, organization_members, resource_groups, monitored_resources, monitored_resource_tags, health_check_configs, health_check_executions, resource_status_history, audit_logs)
- [x] Autenticação: cadastro, login, logout, `/auth/me`, Argon2id, sessão em cookie HttpOnly (`INFRAWATCH_SESSION`), CSRF double-submit (`XSRF-TOKEN` / `X-XSRF-TOKEN`), rate limit em `/auth/*`, proteção contra fixação de sessão
- [x] Organizações multi-tenant: organização ativa resolvida no servidor a partir da sessão + associação; troca de organização validada; RBAC (Administrador / Operador / Visualizador)
- [x] CRUD de recursos monitorados + grupos + tags + configuração de verificação (intervalo ≥ 30 s, timeout, método, códigos esperados, limite de degradação, limiares de falha/recuperação, redirecionamentos)
- [x] Política de alvos / anti-SSRF: allowlist por host (glob), portas, esquema, userinfo, DNS resolvido e validado, bloqueio de loopback/privadas/link-local/metadados/CGNAT/IPv6 ULA e mapeado, redirecionamentos revalidados por salto, limite de bytes e timeout
- [x] Motor de verificações: despachante agendado (claim atômico com `FOR UPDATE SKIP LOCKED`), concorrência limitada por semáforo, virtual threads, "Verificar agora"
- [x] Máquina de estados com histerese (falhas consecutivas → Indisponível; sucessos consecutivos → recuperação; latência → Degradado), resultados fora de ordem ignorados para avaliação, detecção de coleta interrompida (→ Sem dados)
- [x] Histórico: execuções, transições de estado, auditoria
- [x] Dashboard "Visão Geral": KPIs reais, disponibilidade por período, latência por serviço, distribuição de status, recursos por tipo, recursos com problemas, últimas transições, serviços monitorados, filtro de período
- [x] Página de recurso: KPIs, gráfico de latência, verificações, histórico, configuração, pausar/retomar, editar, remover
- [x] Layout: sidebar recolhível, topbar com organização ativa e busca (Ctrl+K), tema escuro padrão + claro, skeletons, estados vazios, toasts, responsivo, atalhos de teclado
- [x] Observabilidade própria: Actuator na porta 8081, Micrometer (`infrawatch.engine.*`), correlation id (`X-Request-Id`), logs ECS no perfil `docker`
- [x] OpenAPI em `/api/v1/docs`
- [x] Seed de demonstração (`INFRAWATCH_SEED_ENABLED=true`)
- [x] Scripts de demonstração (`scripts/demo.sh`, `scripts/demo.ps1`)
- [x] CI (GitHub Actions): backend, frontend, docker, e2e

## Funcionalidades concluídas (Fase 2)

- [x] Migração `V2__metricas_agente.sql`: `agents`, `agent_credentials` (hash SHA-256 + prefixo), `metric_samples` (brutas), `metric_rollups` (5 min / 1 h)
- [x] Registro de agente por recurso do tipo `AGENT_SERVER` (`POST /agents/register`, token exibido uma única vez), rotação (`POST /agents/{id}/rotate`), revogação (`DELETE /agents/{id}`), intervalo (`PATCH /agents/{id}`), listagem (`GET /agents`, `GET /resources/{id}/agent`)
- [x] Ingestão `POST /agents/ingest` (Bearer, sem sessão/CSRF): validação, percentuais calculados no servidor, métricas ausentes gravadas como `NULL`, limite de frequência por agente (429), janela de relógio (400), atualização de `last_seen_at`
- [x] Estado do recurso por agente: amostra ⇒ `UP`; silêncio por `intervalo × 3` ⇒ `UNKNOWN` (Sem dados), nunca `DOWN`
- [x] Leitura `GET /resources/{id}/agent-metrics?period=`: último valor por métrica com flag `available`, lista `missingMetrics`, série com granularidade automática (brutas ≤ 6 h, rollup 5 min ≤ 7 d, rollup 1 h acima), `source: "agent"`
- [x] `MetricMaintenanceJobs`: rollup idempotente a cada 5 min, retenção horária (brutas 7 d, rollups 90 d, execuções HTTP 30 d — configuráveis), detecção de agente silencioso a cada 30 s
- [x] Agente `apps/agent` (Java 21 puro, ~10 KB): CPU, carga, memória, disco, uptime via MXBeans/`FileStore`/`/proc/uptime`; só saída; Dockerfile; README
- [x] Compose: serviço `agent-demo` (perfil `full`) com token conhecido via `INFRAWATCH_DEMO_AGENT_TOKEN`; seed cria "Servidor de demonstração" + agente
- [x] Frontend: `AgentMetricsPanel` (gauges CPU/memória/disco/uptime com "Indisponível", gráficos CPU/mem/disco, etiqueta "Fonte: agente autorizado", aviso de agente silencioso), `AgentPanel` (registrar, token exibido uma vez com comando docker, rotacionar, revogar), formulário aceita tipo servidor (campo vira "Identificação do servidor"), etiqueta "Fonte: verificação HTTP" no gráfico de latência
- [x] Métricas internas: `infrawatch.engine.agent.samples.accepted|rejected`
- [x] Docs atualizadas: monitoring-engine, architecture, security, decisions (17–20), deploy, README; CI constrói o agente

## Funcionalidades concluídas (Fase 3)

- [x] Migração `V3__alertas_notificacoes.sql`: `alert_rules`, `alert_events` (índice único parcial = um alerta ativo por regra×recurso), `alert_condition_states`, `notifications` (dedupe por usuário)
- [x] Eventos de domínio (`common.events.DomainEvents`) publicados por `CheckResultApplier`, `IngestService`, `StaleResourceDetector` e `MetricMaintenanceJobs`
- [x] `AlertEngine` (mesma transação): STATUS_DOWN / STATUS_DEGRADED / AGENT_NO_DATA por transição; LATENCY por verificação; CPU / MEMORY / DISK por amostra; ocorrências consecutivas + duração mínima; histerese (leitura normal resolve); manutenção ignorada
- [x] Sete regras padrão por organização (cadastro, seed e backfill na inicialização)
- [x] API: `GET/POST/PATCH/DELETE /alert-rules`, `GET /alert-rules/options`, `GET /alerts`, `GET /alerts/summary`, `GET /alerts/{id}`, `POST /alerts/{id}/acknowledge|resolve`, `GET /resources/{id}/alerts`, `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`
- [x] Notificações internas por membro (indisponível, recuperado, alerta disparado/resolvido) com `dedupe_key`
- [x] WebSocket STOMP em `/ws`: handshake pela sessão, CONNECT exige Principal, SUBSCRIBE validado por associação à organização, clientes não publicam; `RealtimeBroadcaster` pós-commit com envelope `{id, seq, type, organizationId, occurredAt, payload}`; tópicos `/topic/org/{id}` e `/user/queue/notifications`
- [x] Frontend: `RealtimeProvider` (@stomp/stompjs, backoff exponencial, ressincronização REST em reconexão/salto de sequência, toasts), indicador de tempo real na topbar, sino de notificações, página **Alertas** (KPIs, filtros, drawer com timeline, reconhecer/resolver, aba de regras com CRUD e ativar/desativar), alertas ativos na página do recurso, KPI real e painel "Alertas recentes" no dashboard
- [x] nginx `/ws` com upgrade; Vite proxy `ws:true`
- [x] Métricas internas: `infrawatch.engine.alerts.triggered|resolved`, `infrawatch.engine.realtime.events`

## Funcionalidades concluídas (Fase 4)

- [x] Migração `V4__incidentes_manutencoes.sql`: `incidents` (número sequencial por organização, índice único de incidente automático ativo por recurso), `incident_comments`, `incident_history`, `maintenance_windows` + `maintenance_window_resources`; colunas `auto_incident` e `status_before_maintenance` em `monitored_resources`
- [x] `IncidentEngine` (após o motor de alertas): abre incidente automático crítico ao confirmar `DOWN` (fora de manutenção, recurso com `auto_incident`), vinculado ao alerta; resolve automáticos na recuperação; registra transições do recurso na timeline de incidentes ativos
- [x] `IncidentService`/API: criação manual, PATCH (título, descrição, severidade, responsável, causa, observações — tudo historiado), transições validadas (`allowedTransitions`), comentários, timeline unificada, resumo com MTTR 24 h, alertas/incidentes por recurso; notificações `INCIDENT_CREATED` (organização) e `INCIDENT_ASSIGNED` (responsável)
- [x] `MaintenanceService` + `MaintenanceScheduler`: agendar, editar, encerrar antecipadamente/cancelar; entrada em `MAINTENANCE` resolve alertas ativos (`Resolution.MAINTENANCE`) e suprime alertas/incidentes/notificações; saída → `PENDING` com verificação imediata; notificação `MAINTENANCE_SCHEDULED`
- [x] Disponibilidade com modo de manutenção (`maintenance=excluded|counted`) no dashboard e nas métricas do recurso, com nota explícita
- [x] Dashboard: KPI real de incidentes ativos e de próximas manutenções, gráfico "Incidentes ao longo do tempo" (`GET /dashboard/incidents`), painéis "Últimos incidentes" e "Próximas manutenções", seletor de tratamento de manutenções
- [x] Frontend: páginas **Incidentes** (KPIs, filtros, criar) e **Incidente** (timeline, comentários, ações de status, edição, responsável), **Manutenções** (próximas/em andamento, histórico, agendar com seleção de recursos, encerrar/cancelar); painel de incidentes/manutenções na página do recurso; opção "abrir incidente automaticamente" no formulário; eventos `incident.*` e `maintenance.*` em tempo real
- [x] Docs: alerts-and-incidents (incidentes e manutenções implementados), architecture, decisions 26–29, README

## Testes

| Suíte | Como rodar | Último resultado |
|---|---|---|
| Backend unitários (StatusEvaluator 9, TargetValidator 23, StatusCodeMatcher 4, AvailabilityCalculator 4) | `cd apps/api && ./mvnw test` | 40/40 passando (2026-11-06) |
| Backend integração (Testcontainers + PostgreSQL 17): AuthFlowIT 8, ResourceApiIT 6, CheckEngineIT 8 | idem (mesmo comando, total 62) | 22/22 passando (2026-11-06) |
| Frontend (Vitest + RTL): api client, format, StatusBadge, LoginPage, OverviewPage | `cd apps/web && npm run test:run` | 18/18 passando |
| Backend Fase 2: AgentTokensTest 3, AgentIngestIT 8 (registro, ingestão, métricas ausentes, tokens inválidos/revogados, rotação, 429/400, isolamento, rollup+retenção, agente silencioso) | idem (total 73) | 11/11 passando (2026-11-06) |
| Frontend Fase 2: AgentMetricsPanel (4) | idem | 22/22 no total |
| Backend Fase 3: AlertEngineIT 6 (regras padrão, alerta único + notificações + recuperação, reconhecimento/resolução/RBAC/auditoria, latência com ocorrências, regras de agente + falta de dados, CRUD de regras), WebSocketIT 3 (eventos da própria organização + notificação do usuário, recusa de outra organização, handshake sem sessão) | idem (total 82) | ver "Execução" |
| Frontend Fase 3: NotificationsBell (1) | idem | 23/23 no total |
| Backend Fase 4: IncidentsIT 3 (ciclo automático com alerta vinculado e timeline, ciclo manual com atribuição/comentários/histórico/MTTR/auditoria, isolamento + RBAC + opt-out), MaintenanceIT 2 (supressão de alertas/incidentes, encerramento antecipado e recálculo, agendamento/cancelamento/agendador) | idem (total 87) | ver "Execução" |
| Frontend Fase 4: IncidentsPage (1) | idem | 24/24 no total |
| E2E (Playwright, 3 cenários): cadastro → recurso → verificação real → falha controlada → Indisponível → recuperação → isolamento | `docker compose --profile full up -d && cd e2e && E2E_DEMO_TARGET_HOST=host.docker.internal npx playwright test` | 3/3 passando (dev local e Docker completo) |

### Execução manual validada em 2026-11-06 (Fase 2)

- `docker compose --profile full up -d --build` com banco limpo: seed cria "Servidor de demonstração" + agente; `agent-demo` envia a primeira amostra em ~1 s e o recurso vai de Aguardando → Disponível ("agente passou a reportar").
- `GET /resources/{id}/agent-metrics`: CPU 3,3 %, memória 92,6 % (5,3 GB de 5,7 GB do container), disco 6,7 %, uptime 26 h, carga 2,88, série bruta com 3 pontos após 45 s; painel mostra os gauges, gráficos e a aba Agente (nome, situação "Reportando", prefixo da credencial, rotação/revogação).
- `docker stop infrawatch-agent-demo`: após 45 s o recurso vai para **Sem dados** (nunca Indisponível); `docker start` volta a **Disponível** na amostra seguinte.

### Execução manual validada em 2026-11-06 (Fase 1)

- API sobe, Flyway aplica V1, seed cria Demo Corp + 3 recursos; primeira verificação em ~3 s: saudável/instável → Disponível, degradada → Degradado.
- `POST /__control/down` no serviço instável + 3 × "Verificar agora" → Indisponível (histórico: "3 falha(s) consecutiva(s)"); `up` + 2 × → Disponível ("Recuperado após 2 sucesso(s)").
- Isolamento entre organizações, RBAC (visualizador não cadastra), CSRF (403 `csrf_invalid`), SSRF (`target_not_allowlisted`) verificados via curl e testes.

## Erros conhecidos / limitações

- Ao alternar entre API local e API em Docker usando o **mesmo banco**, recursos cadastrados com `localhost:910x` deixam de ser alcançáveis de dentro do container (passam a `connection_refused` → Indisponível após 3 falhas, como esperado). Use `host.docker.internal:910x` (padrão da seed no perfil `full`) ou atualize a URL do recurso.

- Sessões HTTP ficam em memória (Tomcat): reiniciar a API desloga todos. Spring Session JDBC/Redis previsto para a Fase 5.
- Rate limit em memória por IP (uma instância). Redis quando houver múltiplas instâncias.
- Disponibilidade da Fase 1 não considera manutenções (módulo da Fase 4); a nota é exibida na interface.
- Retenção de `health_check_executions` ainda não é aplicada (política e agregação na Fase 2, junto com as amostras do agente).
- E2E depende do ambiente completo (Docker ou dev local); quando a API roda em container, o recurso de teste aponta para `host.docker.internal:9103` (`E2E_DEMO_TARGET_HOST`).
- O agente usa apenas APIs da JVM: em Windows não há carga média nem uptime do SO (exibidos como indisponíveis). Agente para macOS/Windows nativo não está no escopo.
- Sequência de eventos WebSocket é por instância (memória); com múltiplas instâncias passa a vir de Redis/banco. O cliente ressincroniza via REST em qualquer salto.
- Rollups são gerados a cada 5 min; nos primeiros minutos após instalar um agente a consulta de 7 d usa as amostras brutas (`granularity: raw_fallback`).

## Próximas tarefas (Fase 5)

1. Relatórios: disponibilidade por recurso e período (com/sem manutenção), tempo total indisponível, quantidade de incidentes, MTTR, MTBF (com limitação explícita quando < 2 incidentes), tempo médio e picos de latência, evolução de CPU/memória/disco; página Relatórios com filtros.
2. Exportações CSV e XLSX com histórico de exportações e permissões consistentes.
3. Canais externos consumindo `notifications`: e-mail via SMTP (desativado em dev) e webhooks de saída para destinos previamente cadastrados/autorizados, com fila/outbox e retentativas.
4. Gerenciamento de membros (convite, alteração de perfil, remoção), página de auditoria completa com filtros.
5. Observabilidade: Micrometer Prometheus registry, Prometheus/Grafana opcionais no Compose, OpenTelemetry tracing configurável.
6. Spring Session JDBC (sessões persistentes) e rate limit distribuído se Redis for adicionado.
