# Registro de decisões (ADR resumido)

| # | Decisão | Motivo | Consequências |
|---|---|---|---|
| 1 | **Spring Boot 4.1.2** (não 3.5) | Em nov/2026 a linha 3.5 já saiu do suporte OSS; 4.1.2 é a GA mais recente, compatível com springdoc 3.2.0 e Testcontainers 2.0.5 | Starters modulares (`-webmvc`, `-flyway`, `-micrometer-metrics`), Jackson 3 (`tools.jackson`), `@AutoConfigureMockMvc` em `org.springframework.boot.webmvc.test.autoconfigure` |
| 2 | Monólito modular por domínio | Evitar complexidade artificial; módulos com fronteiras claras permitem extrair serviços depois | Pacotes `auth`, `organization`, `resource`, `monitoring`, `dashboard`, `audit`, `security`, `common` |
| 3 | Sessão em cookie HttpOnly + CSRF double-submit (não JWT) | Requisito do produto; sessões revogáveis, sem token no JS | Spring Session para persistência (Fase 5); frontend lê `XSRF-TOKEN` |
| 4 | Organização ativa resolvida no servidor | Nunca confiar em `organizationId` do cliente | `CurrentContextService` valida a associação a cada requisição |
| 5 | Reivindicação de verificações com `UPDATE … FOR UPDATE SKIP LOCKED … RETURNING` | Atomicidade sem Redis; funciona com várias instâncias | Redis adiado até haver necessidade concreta (rate limit/sessões distribuídas) |
| 6 | UUIDs gerados na aplicação | Identificadores públicos estáveis, independentes do banco | `UUID.randomUUID()` nos construtores das entidades |
| 7 | Tags em tabela própria (`ElementCollection`) em vez de `text[]` | Portabilidade e validação por elemento | Join adicional (carregado EAGER, volume pequeno) |
| 8 | JDK `HttpClient` (não WebClient/RestClient) | Controle fino de redirecionamentos, timeouts e corpo; sem dependência reativa | Risco residual de DNS rebinding documentado em `security.md` |
| 9 | Allowlist obrigatória por padrão (`ALLOWLIST_ONLY`) | O produto monitora recursos autorizados; nunca um scanner | Modo `PUBLIC_AND_ALLOWLIST` existe, mas não é o padrão |
| 10 | Disponibilidade baseada em tempo com estado conhecido | Não inventar 100% sem medições; manutenção/sem dados excluídos | `AvailabilityCalculator`; nota exibida na UI |
| 11 | Polling (10–15 s) na Fase 1 | WebSocket é entrega da Fase 3; a API REST continua sendo a fonte de verdade na reconexão | TanStack Query com `refetchInterval` |
| 12 | shadcn/ui estilo `new-york` (Radix) em vez de `base-nova` (Base UI) | API conhecida e estável, acessibilidade por teclado madura | Dependência `radix-ui` |
| 13 | TanStack Table fixado em **8.x** | 9.x recém-lançado; evitar API em transição | Upgrade avaliado na Fase 6 |
| 14 | Serviços de demonstração em Node sem dependências, controlados por `/__control` | Cenários determinísticos e reproduzíveis; nenhum destino externo | Scripts `scripts/demo.sh` / `demo.ps1` |
| 15 | Postgres na porta **5433** do host | Evitar conflito com instâncias locais na 5432 | `INFRAWATCH_DB_URL` padrão aponta para 5433 |
| 16 | Actuator em porta separada (8081), publicada só em localhost | Não expor endpoints administrativos | Prometheus (Fase 5) coleta na rede interna do Compose |
| 17 | Agente em **Java 21 puro** (sem Spring, sem OSHI) | Mesma linguagem do backend, JAR de ~10 KB, APIs padrão cientes de cgroups; nenhuma dependência para auditar | Métricas limitadas às APIs da JVM (`/proc/uptime` para uptime); Windows sem carga média |
| 18 | Token de agente com prefixo público + hash SHA-256 do todo | Localização O(1) sem armazenar segredo; comparação em tempo constante | Rotação cria nova credencial; prefixo exibido no painel para identificação |
| 19 | Amostras brutas + rollups 5 min/1 h em tabelas separadas | Retenção curta das brutas, histórico longo barato; consultas escolhem a granularidade pela janela | Job idempotente a cada 5 min; fallback para brutas quando rollup ainda não existe |
| 20 | Agente silencioso ⇒ `UNKNOWN`, nunca `DOWN` | Ausência de coleta não é falha comprovada (princípio da Fase 1 mantido) | Alerta "falta de dados do agente" na Fase 3 parte desse estado |
| 21 | STOMP sobre WebSocket nativo (sem SockJS) com interceptor próprio em vez de `@EnableWebSocketSecurity` | Navegadores modernos suportam WebSocket; o interceptor valida membro×organização diretamente e evita exigir token CSRF no CONNECT | Autorização por assinatura em `WebSocketConfig`; sem fallback HTTP long-polling |
| 22 | Eventos de domínio síncronos para o motor de alertas e `AFTER_COMMIT` para o broadcast | Alertas/notificações ficam na mesma transação do fato; clientes nunca veem estado não persistido | `AlertEngine` com `Propagation.MANDATORY`; `RealtimeBroadcaster` com `@TransactionalEventListener` |
| 23 | Um alerta ativo por regra×recurso garantido por índice único parcial | Supressão de duplicados no banco, não só em código | `ux_alert_events_active` |
| 24 | Notificações materializadas por usuário (fan-out) com `dedupe_key` | Leitura simples, "lida" por usuário e idempotência em reprocessamentos | Volume proporcional ao nº de membros; canais externos leem a mesma tabela (Fase 5) |
| 25 | Sequência de eventos em memória por organização | Suficiente para uma instância; o cliente ressincroniza via REST em qualquer salto ou reconexão | Com várias instâncias, a sequência passa a vir de Redis ou do banco |
