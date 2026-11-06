# Arquitetura

O InfraWatch é um **monólito modular**: um único serviço Spring Boot organizado por domínio, um frontend React e serviços auxiliares em Docker. A escolha evita a complexidade artificial de microsserviços e mantém o caminho de crescimento aberto (cada módulo tem controller, service, repository, DTOs e entidades próprios e conversa com os outros por serviços, não por tabelas).

```mermaid
flowchart LR
    B[Navegador] -->|HTTPS / cookies HttpOnly| W[React + TypeScript<br/>Vite · TanStack Query]
    W -->|REST /api/v1| API[Spring Boot 4.1]
    W -.->|WebSocket (Fase 3)| API

    subgraph API [Spring Boot — monólito modular]
        AUTH[auth / organization<br/>sessões · RBAC · CSRF]
        RES[resource<br/>cadastro · política de alvos]
        MON[monitoring<br/>despachante · executor HTTP<br/>máquina de estados]
        MET[metrics<br/>agentes · amostras · rollups]
        DASH[dashboard]
        AUD[audit]
        OBS[actuator · micrometer]
    end

    API --> PG[(PostgreSQL 17<br/>Flyway)]
    AGT[infrawatch-agent<br/>(host do usuário)] -->|POST /agents/ingest · Bearer| API
    MON -->|verificações autorizadas| D1[demo-healthy]
    MON --> D2[demo-degraded]
    MON --> D3[demo-flaky]
    OBS -->|porta 8081| PROM[Prometheus (Fase 5)]
```

## Módulos do backend (`apps/api/src/main/java/com/infrawatch`)

| Pacote | Responsabilidade |
|---|---|
| `common` | Exceções de API (RFC 9457), paginação, correlation id, utilitários |
| `security` | Spring Security 7: Argon2id, sessão em cookie, CSRF para SPA, rate limit, resolução da organização ativa (`CurrentContextService`) |
| `auth` | Usuário, cadastro, login/logout, `/auth/me` |
| `organization` | Organização, associação e perfis (`MemberRole`) |
| `resource` | Recursos monitorados, grupos, configuração de verificação, validação de destino |
| `monitoring` | Política de alvos (anti-SSRF), executor HTTP, despachante, avaliação de estado, histórico, métricas internas |
| `metrics` | Agentes autorizados, credenciais (hash), ingestão de amostras, rollups, retenção, leitura de métricas de CPU/RAM/disco |
| `dashboard` | Agregações para a Visão Geral |
| `audit` | Trilha de auditoria |
| `seed` | Dados de demonstração (opcional) |

## Fluxo de uma requisição autenticada

1. `CorrelationIdFilter` gera/propaga `X-Request-Id` e coloca no MDC.
2. Cadeia do Spring Security: CORS → CSRF (cookie `XSRF-TOKEN` × cabeçalho `X-XSRF-TOKEN`) → contexto de segurança lido da sessão (`INFRAWATCH_SESSION`) → autorização por rota.
3. O controller pede a `CurrentContextService.require()` o contexto: usuário autenticado + organização ativa (atributo de sessão validado contra `organization_members`) + papel. **Nenhum `organizationId` enviado pelo cliente é confiado.**
4. Serviços aplicam regras de negócio e RBAC (`ctx.require(MemberRole.ADMIN)`), repositórios filtram sempre por `organization_id`.
5. Erros viram `ProblemDetail` (`GlobalExceptionHandler`), com `code` estável para o frontend.

## Fluxo de uma verificação

Ver [monitoring-engine.md](monitoring-engine.md).

## Frontend (`apps/web/src`)

| Pasta | Conteúdo |
|---|---|
| `app/` | Provedor de tema |
| `services/` | Cliente HTTP (`api.ts`) e endpoints tipados |
| `features/` | Autenticação (provider, guards), dashboard (KPIs, gráficos), recursos (formulário) |
| `layouts/` | `AppLayout` (sidebar + topbar), `AuthLayout` |
| `pages/` | Visão Geral, Recursos, Detalhe do recurso, Configurações, Login, Cadastro |
| `components/` | Componentes compartilhados (StatusBadge, EmptyState…) e `ui/` (shadcn) |
| `types/` | Contratos da API |

Estado de servidor com TanStack Query (polling de 10–15 s na Fase 1; WebSocket substitui/complementa na Fase 3, mantendo a sincronização via REST ao reconectar).

## Decisões relevantes

Registradas em [decisions.md](decisions.md).
