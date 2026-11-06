# Segurança

## Autenticação e sessão

- Senhas com **Argon2id** (`Argon2PasswordEncoder(16, 32, 1, 19456, 2)` — parâmetros recomendados pela OWASP).
- Sessão servidor persistida em PostgreSQL (**Spring Session JDBC**, tabela `SPRING_SESSION`): sobrevive a reinícios e a várias instâncias. Cookie **HttpOnly**, `SameSite=Lax`, nome `INFRAWATCH_SESSION`, `Secure` configurável (`INFRAWATCH_COOKIE_SECURE=true` atrás de TLS), expiração 12 h (`SessionConfig`).
- **Recuperação de senha**: resposta neutra (não revela contas), token `pwd_…` guardado como hash SHA-256, 1 h, uso único; e-mail só quando SMTP + envio externo estão habilitados, caso contrário o link vai ao log (desenvolvimento). Rotas sob o rate limit de autenticação.
- Login renova o identificador da sessão (`changeSessionId`) — proteção contra fixação.
- Logout invalida a sessão no servidor.
- Respostas de falha de login não distinguem "usuário inexistente" de "senha errada".

## CSRF

Padrão **double submit cookie** recomendado pela documentação do Spring Security para SPAs: cookie `XSRF-TOKEN` (legível por JS) + cabeçalho `X-XSRF-TOKEN` em toda mutation. O token enviado em cabeçalho é comparado em texto puro; em parâmetros de formulário usa-se a variante XOR (anti-BREACH). `GET /api/v1/auth/csrf` apenas garante a emissão do cookie.

## Autorização

- Rotas: só `/auth/register`, `/auth/login`, `/auth/csrf`, `/auth/me`, `/auth/forgot-password`, `/auth/reset-password`, `GET /invites/{token}` (descrição do convite, sem dados sensíveis) e a documentação OpenAPI são públicas; o restante de `/api/v1/**` exige sessão; qualquer outra rota é negada.
- **Multi-organização**: a organização ativa é um atributo de sessão **validado contra a tabela de associação** a cada requisição. IDs vindos do cliente só são aceitos em `POST /organizations/{id}/switch`, que verifica a associação. Todos os repositórios filtram por `organization_id`.
- **RBAC** no backend via `OrganizationContext.require(MemberRole)`: Administrador (cadastra recursos, grupos, membros/convites, canais externos, consulta auditoria), Operador (verificar agora, reconhecer alertas, gerir incidentes e manutenções), Visualizador (somente leitura; pode consultar e exportar relatórios).
- **Membros**: a organização nunca fica sem administrador (troca de papel/remoção do último é recusada com 409). Convites: token exibido uma vez, hash no banco, 7 dias, uso único, vinculado ao e-mail; o aceite exige que o e-mail da conta coincida.

## Rate limiting

`AuthRateLimitFilter`: janela fixa de 1 min por IP em `POST /auth/login`, `/auth/register`, `/auth/forgot-password` e `/auth/reset-password` (20/min por padrão) → `429` com `Retry-After`. Em memória (uma instância); Redis previsto quando houver escala horizontal.

## Proteção contra SSRF nas verificações

Detalhada em [monitoring-engine.md](monitoring-engine.md#execução-http-httpcheckexecutor). Resumo: allowlist obrigatória por padrão (`ALLOWLIST_ONLY`), validação de esquema/host/porta, resolução DNS com bloqueio de faixas internas e metadados, redirecionamentos revalidados, limites de tempo, tamanho e concorrência. O backend **nunca** repassa a resposta do destino ao cliente.

Risco residual documentado: *DNS rebinding* entre a resolução e a conexão (a JDK `HttpClient` não permite fixar o endereço resolvido). Mitigação: política de allowlist (hosts controlados pelo operador) e revalidação por salto. Para o modo `PUBLIC_AND_ALLOWLIST`, um resolvedor fixado será adicionado antes de habilitá-lo por padrão.

## Agentes (Fase 2)

- Token `iwa_<prefixo>.<segredo>` (38 bytes aleatórios, `SecureRandom`), exibido **uma única vez**; o servidor guarda apenas o **SHA-256** e compara em tempo constante. Rotação revoga os anteriores; revogação bloqueia o agente.
- O endpoint `POST /api/v1/agents/ingest` é o único sem sessão/CSRF (Bearer). É somente de entrada: nenhuma resposta carrega comandos, apenas o intervalo sugerido.
- Limite de frequência por agente (mínimo 5 s entre amostras → `429`) e janela de relógio (±5 min à frente / 60 min atrás → `400 sample_clock_skew`).
- O agente não abre portas, não executa comandos, não lê além de `/proc/uptime`, MXBeans e `FileStore`; alerta quando a URL da API não usa TLS fora de redes locais.
- Amostras são validadas (0–100 %, bytes ≥ 0, `usado ≤ total`) e nunca contêm conteúdo arbitrário do host.

## WebSocket (Fase 3)

- O handshake `/ws` passa pela cadeia do Spring Security (rota autenticada; cookie de sessão HttpOnly). `CONNECT` sem Principal é recusado.
- `SUBSCRIBE` a `/topic/org/{id}` só é aceito se o usuário for membro da organização (consulta a `organization_members` no servidor). Filas `/user/queue/*` são resolvidas pelo próprio Principal.
- Clientes não enviam mensagens de aplicação; `SEND` é recusado. Origens permitidas = origens CORS.
- Eventos só são emitidos **após o commit** da transação (nunca há evento de estado não persistido) e carregam apenas dados já acessíveis pela API REST.

## Validação e erros

- Bean Validation em todos os DTOs de entrada; limites de tamanho em todos os campos.
- Erros padronizados RFC 9457 (`application/problem+json`) com `code` estável; mensagens internas nunca vazam (500 genérico + log com correlation id).
- `server.error.include-message/stacktrace: never`.

## Integrações externas (Fase 5)

- **Webhooks de saída** só para destinos previamente cadastrados por um administrador e aprovados pela **mesma política anti-SSRF das verificações** (`TargetValidator`, allowlist obrigatória, faixas privadas/metadados bloqueadas, sem redirecionamentos), validada na criação e repetida a cada envio (DNS pode mudar). O corpo da resposta do destino é descartado; só o código HTTP é registrado. O InfraWatch nunca atua como proxy.
- Assinatura **HMAC-SHA256** do corpo (`X-InfraWatch-Signature`) quando o canal tem segredo; o segredo nunca é devolvido pela API (`hasSecret`).
- **Envio externo desligado por padrão** (`INFRAWATCH_INTEGRATIONS_ENABLED=false`): entregas ficam `SKIPPED` e nada sai da máquina — adequado a desenvolvimento e testes. Os testes automatizados usam `JavaMailSender` simulado e um servidor HTTP interno; nenhuma notificação real é enviada.
- Conteúdo das entregas: apenas título, mensagem, severidade, tipo, link e identificadores — nunca cookies, tokens ou corpos de respostas monitoradas.
- **Exportações**: isoladas por organização (404 fora dela), auditadas (`report.exported`), CSV com proteção contra *CSV injection*.

## Cabeçalhos HTTP (revisão da Fase 6)

| Onde | Cabeçalhos |
|---|---|
| SPA (nginx) | `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' ws: wss:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'` (só nas respostas da SPA; `/api` e `/ws` mantêm os cabeçalhos da própria API), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`, `Cross-Origin-Opener-Policy: same-origin`, `server_tokens off`, `index.html` sem cache e assets imutáveis |
| API (Spring Security) | `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `Cache-Control: no-store` |
| Caddy (perfil `tls`) | `Strict-Transport-Security` (1 ano) e remoção do cabeçalho `Server` |

`script-src` não usa `unsafe-inline` nem `unsafe-eval`; `unsafe-inline` existe apenas em `style-src` por causa dos estilos inline de Recharts/Radix (nonces ficam como melhoria futura). Verificado por `SecurityHeadersIT` (API) e pelo E2E no Docker (SPA).

## Revisão de segurança (Fase 6)

Resumo em [security-review.md](security-review.md): rotas públicas mínimas, varredura de isolamento automatizada (`IsolationSweepIT`), testes de concorrência (`ConcurrencyIT`), auditoria de dependências (`npm audit` em CI, Dependabot, CodeQL), erros sem vazamento, anti-SSRF nas verificações e nos webhooks, envio externo desligado por padrão.

## Observabilidade segura

- Actuator responde **apenas na porta de gerenciamento 8081**, que no Compose é publicada só em `127.0.0.1`. Não há endpoints administrativos na porta pública.
- Logs estruturados (ECS) no perfil `docker`; o `correlationId` acompanha cada requisição e o `runId` cada verificação.

## Auditoria

`audit_logs` registra cadastro, login, recursos, grupos, alertas, incidentes, manutenções, membros/convites, canais externos, exportações e redefinições de senha, com usuário, organização, IP e detalhes. Consultável por administradores em `GET /api/v1/audit` com filtros (ação, usuário, intervalo, busca) e nome do usuário resolvido.

## Checklist para produção

- TLS na borda e `INFRAWATCH_COOKIE_SECURE=true`.
- `INFRAWATCH_ALLOWED_HOSTS` restrito aos destinos reais.
- Segredos via variáveis de ambiente/secret manager (não há segredos no repositório).
- Rate limit distribuído (Redis) se houver várias instâncias — as sessões já são persistentes (Spring Session JDBC).
