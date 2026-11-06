# Segurança

## Autenticação e sessão

- Senhas com **Argon2id** (`Argon2PasswordEncoder(16, 32, 1, 19456, 2)` — parâmetros recomendados pela OWASP).
- Sessão servidor (`HttpSession`) em cookie **HttpOnly**, `SameSite=Lax`, nome `INFRAWATCH_SESSION`, `Secure` configurável (`INFRAWATCH_COOKIE_SECURE=true` atrás de TLS), expiração 12 h.
- Login renova o identificador da sessão (`changeSessionId`) — proteção contra fixação.
- Logout invalida a sessão no servidor.
- Respostas de falha de login não distinguem "usuário inexistente" de "senha errada".

## CSRF

Padrão **double submit cookie** recomendado pela documentação do Spring Security para SPAs: cookie `XSRF-TOKEN` (legível por JS) + cabeçalho `X-XSRF-TOKEN` em toda mutation. O token enviado em cabeçalho é comparado em texto puro; em parâmetros de formulário usa-se a variante XOR (anti-BREACH). `GET /api/v1/auth/csrf` apenas garante a emissão do cookie.

## Autorização

- Rotas: só `/auth/register`, `/auth/login`, `/auth/csrf`, `/auth/me` e a documentação OpenAPI são públicas; o restante de `/api/v1/**` exige sessão; qualquer outra rota é negada.
- **Multi-organização**: a organização ativa é um atributo de sessão **validado contra a tabela de associação** a cada requisição. IDs vindos do cliente só são aceitos em `POST /organizations/{id}/switch`, que verifica a associação. Todos os repositórios filtram por `organization_id`.
- **RBAC** no backend via `OrganizationContext.require(MemberRole)`: Administrador (cadastra recursos, grupos, consulta auditoria), Operador (verificar agora; na Fase 3/4: reconhecer alertas e gerir incidentes), Visualizador (somente leitura).

## Rate limiting

`AuthRateLimitFilter`: janela fixa de 1 min por IP em `POST /auth/login` e `/auth/register` (20/min por padrão) → `429` com `Retry-After`. Em memória (uma instância); Redis previsto quando houver escala horizontal.

## Proteção contra SSRF nas verificações

Detalhada em [monitoring-engine.md](monitoring-engine.md#execução-http-httpcheckexecutor). Resumo: allowlist obrigatória por padrão (`ALLOWLIST_ONLY`), validação de esquema/host/porta, resolução DNS com bloqueio de faixas internas e metadados, redirecionamentos revalidados, limites de tempo, tamanho e concorrência. O backend **nunca** repassa a resposta do destino ao cliente.

Risco residual documentado: *DNS rebinding* entre a resolução e a conexão (a JDK `HttpClient` não permite fixar o endereço resolvido). Mitigação: política de allowlist (hosts controlados pelo operador) e revalidação por salto. Para o modo `PUBLIC_AND_ALLOWLIST`, um resolvedor fixado será adicionado antes de habilitá-lo por padrão.

## Agentes (Fase 2)

- Token `iwa_<prefixo>.<segredo>` (38 bytes aleatórios, `SecureRandom`), exibido **uma única vez**; o servidor guarda apenas o **SHA-256** e compara em tempo constante. Rotação revoga os anteriores; revogação bloqueia o agente.
- O endpoint `POST /api/v1/agents/ingest` é o único sem sessão/CSRF (Bearer). É somente de entrada: nenhuma resposta carrega comandos, apenas o intervalo sugerido.
- Limite de frequência por agente (mínimo 5 s entre amostras → `429`) e janela de relógio (±5 min à frente / 60 min atrás → `400 sample_clock_skew`).
- O agente não abre portas, não executa comandos, não lê além de `/proc/uptime`, MXBeans e `FileStore`; alerta quando a URL da API não usa TLS fora de redes locais.
- Amostras são validadas (0–100 %, bytes ≥ 0, `usado ≤ total`) e nunca contêm conteúdo arbitrário do host.

## Validação e erros

- Bean Validation em todos os DTOs de entrada; limites de tamanho em todos os campos.
- Erros padronizados RFC 9457 (`application/problem+json`) com `code` estável; mensagens internas nunca vazam (500 genérico + log com correlation id).
- `server.error.include-message/stacktrace: never`.

## Observabilidade segura

- Actuator responde **apenas na porta de gerenciamento 8081**, que no Compose é publicada só em `127.0.0.1`. Não há endpoints administrativos na porta pública.
- Logs estruturados (ECS) no perfil `docker`; o `correlationId` acompanha cada requisição e o `runId` cada verificação.

## Auditoria

`audit_logs` registra cadastro, login, criação/alteração/remoção de recursos e grupos com usuário, organização, IP e detalhes. Consultável por administradores em `GET /api/v1/audit`.

## Checklist para produção (fora do escopo da Fase 1)

- TLS na borda e `INFRAWATCH_COOKIE_SECURE=true`.
- `INFRAWATCH_ALLOWED_HOSTS` restrito aos destinos reais.
- Segredos via variáveis de ambiente/secret manager (não há segredos no repositório).
- Spring Session (JDBC/Redis) para sessões persistentes.
