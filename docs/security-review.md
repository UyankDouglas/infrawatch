# Revisão de segurança (Fase 6)

Revisão feita sobre o código da versão 1.0.0, orientada pelas categorias do OWASP Top 10 e pelos requisitos do
projeto (monitor, nunca scanner; nada de credenciais ou corpos de resposta armazenados; sem envio externo em dev).

## Resultado por área

| Área | Situação | Evidência |
|---|---|---|
| Controle de acesso | ✅ RBAC no backend, organização ativa resolvida no servidor, último administrador protegido | `IsolationSweepIT` (44 rotas), `AuthFlowIT`, `MembersIT` |
| Autenticação e sessão | ✅ Argon2id, sessão persistida (Spring Session JDBC), cookie HttpOnly/SameSite=Lax/Secure configurável, renovação de id no login, logout invalida | `AuthFlowIT`, `PasswordResetIT` |
| CSRF | ✅ double submit com cabeçalho; ingestão de agentes fora da sessão (Bearer) | `AuthFlowIT.mutationsRequireCsrf` |
| SSRF | ✅ allowlist obrigatória, bloqueio de redes privadas/metadados/loopback, redirecionamentos revalidados, limites; a mesma política nos webhooks, na criação e a cada envio | `TargetValidatorTest` (23), `CheckEngineIT`, `IntegrationsIT.webhookTargetPolicy` |
| Injeção | ✅ JPA/parametrização em todas as consultas (nativas com `:param`); CSV com neutralização de fórmulas | `ReportsIT.csvInjectionIsNeutralized` |
| Exposição de dados | ✅ corpo das respostas monitoradas nunca persistido; tokens (agente, convite, redefinição) só como hash; segredo do webhook nunca devolvido; erros RFC 9457 sem stack trace | `SecurityHeadersIT`, `PasswordResetIT`, `MembersIT` |
| Cabeçalhos | ✅ CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, `COOP` (SPA) e `Cache-Control: no-store` (API); HSTS no Caddy | `SecurityHeadersIT`, `apps/web/nginx.conf` |
| Rate limiting | ✅ login, cadastro, esqueci/redefinir senha (por IP); ingestão de agentes por credencial | `AuthRateLimitFilter`, `AgentIngestIT` |
| Dependências | ✅ `npm audit --omit=dev`: 0 vulnerabilidades (a CLI `shadcn` foi movida para devDependencies); Spring Boot 4.1.2 / Tomcat 11.0.27 / PostgreSQL JDBC 42.7.14 / POI 5.4.1 / commons-compress 1.27.1; Dependabot semanal e CodeQL (Java + TS) ativos | `.github/dependabot.yml`, `.github/workflows/codeql.yml`, passo de auditoria no CI |
| Concorrência | ✅ claims com `FOR UPDATE SKIP LOCKED`, índices únicos parciais (alerta/incidente ativo), outbox exatamente-uma-vez, cadastro concorrente → 409 | `ConcurrencyIT` |
| Observabilidade | ✅ Actuator só na porta 8081 em `127.0.0.1`; health de e-mail desligado; logs sem segredos | `docker-compose.yml`, `application.yml` |
| Supply chain do agente | ✅ JAR sem dependências, token com prefixo + hash, rotação/revogação | `AgentTokensTest`, `AgentIngestIT` |

## Riscos residuais aceitos (documentados)

1. **DNS rebinding** entre a resolução e a conexão nas verificações: a JDK `HttpClient` não permite fixar o endereço.
   Mitigação: allowlist por padrão (hosts controlados pelo operador) e revalidação por salto. O modo
   `PUBLIC_AND_ALLOWLIST` não é o padrão e deve esperar um resolvedor fixado.
2. **`style-src 'unsafe-inline'`** na CSP da SPA, exigido por Recharts/Radix. Scripts seguem restritos à própria
   origem; nonces ficam como melhoria.
3. **Rate limit por instância** (memória). Com várias instâncias atrás de um balanceador, mover para Redis.
4. **Segredo do webhook em texto no banco** (necessário para assinar). Mitigação: só administradores o definem, nunca
   é lido pela API; criptografia em repouso fica a cargo do banco/volume.

## Itens verificados manualmente

- `GET /api/v1/auth/me` sem sessão → 401 com corpo neutro; `/admin`, `/actuator` na porta 8080 → negado.
- Convite com e-mail diferente, token reutilizado, revogado e expirado → recusados com mensagens específicas e sem
  revelar o e-mail do dono para quem não tem o token.
- Exportação de outra organização por id → 404 (não 403, para não confirmar existência).
- Webhook para `169.254.169.254`, `10.0.0.5`, domínio externo e `ftp://` → `400 target_not_allowlisted` / esquema.
- Com `INFRAWATCH_INTEGRATIONS_ENABLED=false`, nenhuma conexão de saída é aberta para canais (entregas `SKIPPED`).
