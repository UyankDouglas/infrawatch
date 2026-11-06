# Integrações externas e administração (Fase 5)

## Canais de notificação (e-mail e webhook)

As notificações internas (sino) continuam sendo a fonte: `NotificationService.notifyOrganization` enfileira, **na mesma
transação**, uma linha em `notification_deliveries` (outbox) para cada canal elegível. Nada é enviado dentro da
transação — o `DeliveryDispatcher` roda a cada 10 s, reivindica as entregas vencidas com `FOR UPDATE SKIP LOCKED`
(seguro com várias instâncias) e tenta a entrega fora do banco.

| Campo do canal | Efeito |
|---|---|
| `type` | `EMAIL` (SMTP via `JavaMailSender`) ou `WEBHOOK` (HTTP POST JSON) |
| `minSeverity` | entregas só para eventos com severidade ≥ mínima |
| `eventTypes` | filtro opcional por tipo (`RESOURCE_DOWN`, `INCIDENT_CREATED`, …); vazio = todos |
| `enabled` | canal desativado não recebe nada (as internas continuam) |
| `webhookSecret` | se definido, cada POST leva `X-InfraWatch-Signature: sha256=<HMAC-SHA256 do corpo>` |

### Garantias

- **Sem duplicidade**: índice único `(channel_id, dedupe_key)` — a mesma chave de deduplicação usada pelas notificações
  internas evita reenvios em reprocessamentos.
- **Exatamente uma vez entre despachantes**: ao reivindicar, o despachante adianta `next_attempt_at` em 60 s (lease); assim outra instância não reenvia a mesma entrega enquanto o envio está em curso. Se o processo cair no meio, a entrega volta a ser elegível após o prazo (semântica *at-least-once* só em caso de falha do processo). Garantia coberta por `ConcurrencyIT.outboxDeliversExactlyOnce`, que encontrou essa janela na Fase 6.
- **Retentativas com espera progressiva**: 30 s, 60 s, 2 min, 4 min… (máx. 1 h) até `max-attempts` (5) → `FAILED`.
  Entregas com mais de `expire-after` (6 h) na fila viram `EXPIRED` (não se envia alerta velho).
- **Desativado por padrão em desenvolvimento**: `infrawatch.integrations.dispatch-enabled=false`
  (`INFRAWATCH_INTEGRATIONS_ENABLED`). Com o envio desligado, as entregas são registradas como `SKIPPED` com o motivo e
  **nada sai da máquina**. O painel mostra esse aviso na aba Integrações. Testes automatizados nunca usam SMTP real:
  o e-mail é verificado com `JavaMailSender` simulado e o webhook contra um servidor HTTP iniciado dentro do teste.
- **Anti-SSRF**: a URL do webhook passa pelo mesmo `TargetValidator` das verificações, na criação **e** a cada envio
  (allowlist obrigatória, bloqueio de redes privadas/metadados, sem redirecionamentos). O InfraWatch nunca vira um
  proxy: o corpo da resposta do webhook é descartado; só o código HTTP é registrado.
- **Conteúdo mínimo**: título, mensagem, severidade, tipo, link para o painel, `resourceId`, `organizationId`, horário.
  Nenhum cookie, token ou corpo de resposta monitorada é incluído.

### Corpo do webhook

```json
{
  "event": "RESOURCE_DOWN",
  "severity": "CRITICAL",
  "severityLabel": "Crítico",
  "title": "Indisponibilidade: API Pagamentos",
  "message": "3 falha(s) consecutiva(s)…",
  "link": "http://localhost:5174/recursos/<id>",
  "resourceId": "<uuid>",
  "organizationId": "<uuid>",
  "occurredAt": "2026-11-06T18:00:00Z",
  "source": "infrawatch"
}
```

Cabeçalhos: `Content-Type: application/json`, `X-InfraWatch-Event`, `X-InfraWatch-Delivery` (id para idempotência no
receptor), `X-InfraWatch-Signature` (quando há segredo), `User-Agent: InfraWatch-Webhook/1.0`.

### Testando localmente

1. `docker compose --profile full --profile observability up -d --build` com `INFRAWATCH_INTEGRATIONS_ENABLED=true` e
   `INFRAWATCH_SMTP_HOST=mailpit` no `.env`.
2. **E-mail**: crie um canal `EMAIL` em Configurações → Integrações, clique **Testar** e veja a mensagem no Mailpit
   (http://127.0.0.1:8025). O Mailpit não entrega nada para fora.
3. **Webhook**: os serviços de demonstração expõem um receptor em `POST /__webhook` e listam o que receberam em
   `GET /__webhook/received` (ex.: `http://localhost:9101/__webhook/received`). Dentro do Docker use
   `http://host.docker.internal:9101/__webhook` (allowlist).

### Endpoints

`GET|POST /api/v1/integrations/channels`, `PATCH|DELETE /channels/{id}`, `POST /channels/{id}/test`,
`GET /channels/options` (tipos de evento), `GET /integrations/deliveries?channelId=&page=` — todos exigem Administrador.

## Membros e convites

- `PATCH /organizations/current/members/{userId}` (`{role}`) e `DELETE …/members/{userId}` — Administrador; a
  organização **nunca fica sem administrador** (409).
- Convites: `POST /organizations/current/invites` (`{email, role}`) devolve o **token uma única vez** (`inv_…`); no
  banco fica só o hash SHA-256. Expira em 7 dias, uso único, vinculado ao e-mail. `GET /invites/{token}` é público e
  descreve o convite para a tela `/convite/{token}`; o aceite acontece por `POST /auth/register` com `inviteToken`
  (usuário novo entra na organização em vez de criar outra) ou `POST /invites/{token}/accept` (usuário já logado com o
  mesmo e-mail; a organização ativa muda para a nova). Revogação: `DELETE …/invites/{id}`.
- Tudo é auditado: `member.invited`, `member.joined`, `member.role_changed`, `member.removed`, `member.invite_revoked`.

## Recuperação de senha

`POST /auth/forgot-password` sempre responde a mesma mensagem (não revela e-mails cadastrados) e está sob o rate limit de
autenticação. Token `pwd_…` com hash no banco, válido por 1 h, uso único. O link é enviado por e-mail quando há SMTP e o
envio externo está habilitado; caso contrário é registrado no log do servidor (`[DEV] Link de redefinição…`).
`POST /auth/reset-password` (`{token, password}`) troca a senha e invalida o token.

## Auditoria com filtros

`GET /api/v1/audit?action=&userId=&from=&to=&search=&page=&size=` (prefixo de ação, usuário, intervalo, busca nos
detalhes) devolve também `userName`; `GET /api/v1/audit/actions` lista as ações existentes para o filtro.

## Sessões persistentes

Spring Session JDBC (`SPRING_SESSION`, criada pela migração V5): reiniciar a API não desloga ninguém e várias instâncias
compartilham as sessões. O cookie continua `INFRAWATCH_SESSION`, HttpOnly, `SameSite=Lax`, `Secure` por
`INFRAWATCH_COOKIE_SECURE` (`SessionConfig`).
