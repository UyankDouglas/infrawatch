# Estratégia de testes

Princípio: **nenhum mock onde dá para usar o real**. PostgreSQL via Testcontainers, servidores HTTP embutidos como
destinos das verificações e dos webhooks, WebSocket contra o servidor real, Playwright contra o ambiente Docker completo.
O único duplo de teste é o `JavaMailSender` (para nunca enviar e-mail de verdade).

| Camada | Ferramentas | O que cobre | Como rodar |
|---|---|---|---|
| Unitários (backend) | JUnit 5 | máquina de estados (`StatusEvaluator`), política de alvos (`TargetValidator`), códigos HTTP, cálculo de disponibilidade, tokens, CSV | `cd apps/api && ./mvnw test` |
| Integração (backend) | Spring Boot Test + MockMvc + Testcontainers (PostgreSQL 17) | autenticação/sessão/CSRF, CRUD e validações, motor de verificações contra servidor HTTP embutido, agentes e ingestão, alertas, WebSocket (porta real), incidentes, manutenções, relatórios e exportações, canais externos, membros/convites, recuperação de senha | idem (`*IT`) |
| Qualidade (backend) | idem, com threads reais | **concorrência** (`ConcurrencyIT`), **isolamento entre organizações** (`IsolationSweepIT`), **cabeçalhos e erros** (`SecurityHeadersIT`) | idem |
| Benchmark | JUnit opcional | seed sintético e medições p50/p95 (`BenchmarkIT`) | `./mvnw test -Dtest=BenchmarkIT -Dinfrawatch.bench=true` |
| Frontend | Vitest + Testing Library (jsdom) | cliente HTTP (CSRF, erros), formatação, badges, Login, Visão Geral, Incidentes, Relatórios, Integrações, painel do agente, sino de notificações | `cd apps/web && npm run test:run` |
| E2E | Playwright (Chromium) | fluxo completo da Fase 1 (cadastro → recurso → falha controlada → recuperação → isolamento) e da Fase 5 (relatório + exportação, convite, canal webhook) contra `docker compose --profile full` | `cd e2e && E2E_DEMO_TARGET_HOST=host.docker.internal npx playwright test` |
| Capturas | Playwright | gera `docs/screenshots/` a partir do ambiente real | `SCREENSHOTS=1 npx playwright test tests/screenshots.spec.ts` |

## Convenções

- Testes de integração estendem `AbstractIntegrationTest`: um único container PostgreSQL por JVM, Flyway aplicado,
  sessão autenticada pelo **cookie real** (`Session.cookie()`), helpers `register`, `login`, `createResource`, `getJson`.
- Cada teste cria a própria organização; nada depende de ordem ou de limpeza do banco.
- Resultados de verificação são injetados por `CheckResultApplier.apply(...)` quando o que está em teste é a
  consequência (alerta, incidente, relatório), e executados de verdade (`CheckEngineIT`) quando o que está em teste é o
  executor HTTP.
- O despachante de verificações fica desligado no perfil `test` (`infrawatch.monitoring.enabled=false`); o despachante
  do outbox continua ligado e os testes toleram sua participação (o invariante é "exatamente uma vez").
- Falhas de isolamento são tratadas como bug de segurança: a lista de rotas em `IsolationSweepIT` deve crescer junto
  com a API.

## Números de referência (2026-11-06)

| Suíte | Quantidade |
|---|---|
| Backend (unitários + integração + qualidade) | 104 |
| Frontend | 28 |
| E2E | 6 cenários |

CI (`.github/workflows/ci.yml`): backend, frontend (lint, typecheck, `npm audit --omit=dev`, testes, build), agente,
imagens Docker (inclui validação do Compose com todos os perfis) e E2E. CodeQL e Dependabot complementam.
