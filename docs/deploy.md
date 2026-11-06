# Execução e deploy

O InfraWatch foi projetado para rodar **localmente, sem serviços pagos**. Deploy público não é requisito.

## Pré-requisitos

- Docker Desktop (ou Docker Engine + Compose v2)
- Para desenvolvimento: JDK 21, Node 22 (Maven vem pelo wrapper)

## Modo 1 — tudo em Docker (demonstração)

```bash
cp .env.example .env
docker compose --profile full up -d --build
```

| Serviço | URL |
|---|---|
| Web | http://localhost:5174 |
| API | http://localhost:8080/api/v1 (OpenAPI: `/api/v1/docs`) |
| Actuator | http://127.0.0.1:8081/actuator/health |
| Demos | http://localhost:9101 · 9102 · 9103 (`/health`, `/__control`) |
| Agente demo | container `infrawatch-agent-demo` (sem portas) reportando para a API a cada 15 s |
| PostgreSQL | localhost:5433 (`infrawatch`/`infrawatch`) |

A seed (`INFRAWATCH_SEED_ENABLED=true`, padrão no perfil `full`) cria:

| Usuário | Perfil | Senha |
|---|---|---|
| `admin@demo.infrawatch.local` | Administrador | `InfraWatch@2026` |
| `operador@demo.infrawatch.local` | Operador | `InfraWatch@2026` |
| `visualizador@demo.infrawatch.local` | Visualizador | `InfraWatch@2026` |
| `admin@outra.infrawatch.local` | Administrador de outra organização (isolamento) | `InfraWatch@2026` |

Dentro do Docker, a API alcança os serviços de demonstração por `host.docker.internal:910x` (configurado em `INFRAWATCH_DEMO_BASE_URL`) ou pelos nomes `demo-healthy`/`demo-degraded`/`demo-flaky`, todos na allowlist.

## Modo 2 — desenvolvimento

```bash
docker compose up -d postgres demo-healthy demo-degraded demo-flaky
```

```bash
cd apps/api && INFRAWATCH_SEED_ENABLED=true ./mvnw spring-boot:run
```

```bash
cd apps/web && npm install && npm run dev
```

O Vite (porta 5174) faz proxy de `/api` para `localhost:8080`, então cookies de sessão e CSRF funcionam no mesmo domínio.

## Variáveis de ambiente (API)

| Variável | Padrão | Descrição |
|---|---|---|
| `INFRAWATCH_DB_URL` | `jdbc:postgresql://localhost:5433/infrawatch` | |
| `INFRAWATCH_DB_USER` / `INFRAWATCH_DB_PASSWORD` | `infrawatch` | |
| `INFRAWATCH_API_PORT` / `INFRAWATCH_MANAGEMENT_PORT` | `8080` / `8081` | |
| `INFRAWATCH_CORS_ORIGINS` | `http://localhost:5174` | lista separada por vírgula |
| `INFRAWATCH_COOKIE_SECURE` | `false` | `true` atrás de TLS |
| `INFRAWATCH_MONITORING_ENABLED` | `true` | desliga o despachante (útil em testes) |
| `INFRAWATCH_ALLOWED_HOSTS` | `localhost,127.0.0.1,host.docker.internal,demo-*` | allowlist de destinos (glob) |
| `INFRAWATCH_ALLOWED_PORTS` | `80,443,8080,8081,9101,9102,9103` | vazio = qualquer porta |
| `INFRAWATCH_SEED_ENABLED` | `false` | seed de demonstração |
| `INFRAWATCH_DEMO_BASE_URL` | `http://localhost` | base das URLs da seed |
| `INFRAWATCH_DEMO_AGENT_TOKEN` | vazio | token do agente de demonstração criado pela seed (perfil `full` define um valor local) |
| `INFRAWATCH_METRICS_RAW_RETENTION` / `INFRAWATCH_METRICS_ROLLUP_RETENTION` / `INFRAWATCH_CHECK_RETENTION` | `7d` / `90d` / `30d` | retenção de amostras, rollups e execuções HTTP |
| `SPRING_PROFILES_ACTIVE` | — | `docker` ativa logs JSON (ECS) |

Parâmetros do motor (`infrawatch.monitoring.*` em `application.yml`): `dispatcher-interval` (5 s), `max-concurrent-checks` (8), `batch-size` (50), `min-interval-seconds` (30), `max-timeout-ms` (30000), `max-response-bytes` (65536), `max-redirects` (3), `stale-after-multiplier` (3).

## Produção (opcional, Fase 6)

`docker-compose.prod.yml` (a criar na Fase 6) adicionará: imagens versionadas, TLS via proxy reverso, `INFRAWATCH_COOKIE_SECURE=true`, volumes com backup, Prometheus + Grafana opcionais e limites de recursos.

## Instalar o agente em um servidor seu

1. Cadastre um recurso do tipo **Servidor com agente autorizado** (informe só o nome do host).
2. Na aba **Agente**, clique **Registrar agente**: o token aparece uma única vez.
3. No servidor: `docker run -d -e INFRAWATCH_API_URL=https://seu-infrawatch -e INFRAWATCH_AGENT_TOKEN=iwa_… infrawatch-agent` (imagem construída de `apps/agent`) ou `java -jar infrawatch-agent.jar`.
4. Em até um intervalo o painel mostra CPU, memória, disco e uptime com a etiqueta **Fonte: agente autorizado**.
