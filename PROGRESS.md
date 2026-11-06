# PROGRESS.md — InfraWatch

> Arquivo de retomada. Se uma sessão for interrompida, comece lendo este arquivo.

## Fase atual

**Fase 1 — Fundação e monitoramento funcional: CONCLUÍDA (2026-11-06).**
**Fase 2 — Métricas de infraestrutura (agente autorizado): CONCLUÍDA (2026-11-06).**
Próxima: **Fase 3 — Tempo real (WebSocket) e alertas**.

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

## Testes

| Suíte | Como rodar | Último resultado |
|---|---|---|
| Backend unitários (StatusEvaluator 9, TargetValidator 23, StatusCodeMatcher 4, AvailabilityCalculator 4) | `cd apps/api && ./mvnw test` | 40/40 passando (2026-11-06) |
| Backend integração (Testcontainers + PostgreSQL 17): AuthFlowIT 8, ResourceApiIT 6, CheckEngineIT 8 | idem (mesmo comando, total 62) | 22/22 passando (2026-11-06) |
| Frontend (Vitest + RTL): api client, format, StatusBadge, LoginPage, OverviewPage | `cd apps/web && npm run test:run` | 18/18 passando |
| Backend Fase 2: AgentTokensTest 3, AgentIngestIT 8 (registro, ingestão, métricas ausentes, tokens inválidos/revogados, rotação, 429/400, isolamento, rollup+retenção, agente silencioso) | idem (total 73) | 11/11 passando (2026-11-06) |
| Frontend Fase 2: AgentMetricsPanel (4) | idem | 22/22 no total |
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
- Rollups são gerados a cada 5 min; nos primeiros minutos após instalar um agente a consulta de 7 d usa as amostras brutas (`granularity: raw_fallback`).

## Próximas tarefas (Fase 3)

1. Spring WebSocket (STOMP sobre SockJS ou WebSocket puro) com autenticação pela sessão e tópico por organização; eventos `resource.status_changed`, `resource.check_completed`, `alert.triggered`, `alert.acknowledged`, com `version`/sequência.
2. Cliente WebSocket no frontend com reconexão exponencial e ressincronização via REST ao reconectar.
3. Entidades `AlertRule`, `AlertEvent`, `Notification` + migração V3; motor de alertas acionado por transições de estado e por amostras do agente (CPU/RAM/disco acima do limite, falta de dados).
4. Severidades, duração mínima, supressão de duplicados, reconhecimento/resolução, histórico.
5. Notificações internas (sino na topbar) com outbox.
6. Páginas Alertas e regras; KPI "Alertas ativos" real no dashboard.
