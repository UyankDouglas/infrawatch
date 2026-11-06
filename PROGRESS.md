# PROGRESS.md — InfraWatch

> Arquivo de retomada. Se uma sessão for interrompida, comece lendo este arquivo.

## Fase atual

**Fase 1 — Fundação e monitoramento funcional: CONCLUÍDA (2026-11-06).**
Próxima: **Fase 2 — Métricas de infraestrutura (agente autorizado)**.

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

## Testes

| Suíte | Como rodar | Último resultado |
|---|---|---|
| Backend unitários (StatusEvaluator 9, TargetValidator 23, StatusCodeMatcher 4, AvailabilityCalculator 4) | `cd apps/api && ./mvnw test` | 40/40 passando (2026-11-06) |
| Backend integração (Testcontainers + PostgreSQL 17): AuthFlowIT 8, ResourceApiIT 6, CheckEngineIT 8 | idem (mesmo comando, total 62) | 22/22 passando (2026-11-06) |
| Frontend (Vitest + RTL): api client, format, StatusBadge, LoginPage, OverviewPage | `cd apps/web && npm run test:run` | 18/18 passando |
| E2E (Playwright, 3 cenários): cadastro → recurso → verificação real → falha controlada → Indisponível → recuperação → isolamento | `docker compose --profile full up -d && cd e2e && E2E_DEMO_TARGET_HOST=host.docker.internal npx playwright test` | 3/3 passando (dev local e Docker completo) |

### Execução manual validada em 2026-11-06

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
- Tipo `AGENT_SERVER` existe no modelo, mas só ganha coleta na Fase 2 (o formulário o desabilita).

## Próximas tarefas (Fase 2)

1. Entidades `Agent`, `AgentCredential`, `MetricSample` + migração V2.
2. Endpoint autenticado de ingestão (`POST /api/v1/agents/{id}/samples`) com token por agente (hash) e limite de taxa.
3. Agente Java leve (JAR) usando OSHI ou `OperatingSystemMXBean` + imagem Docker de demonstração.
4. Métricas CPU / RAM / disco / uptime na página do recurso, com "métrica indisponível" quando o agente não reporta.
5. Retenção e agregação (5 min / 1 h) para amostras e execuções.
6. Alerta de "falta de dados do agente" (base para a Fase 3).
