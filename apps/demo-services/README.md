# Serviços de demonstração

Três instâncias do mesmo servidor Node (sem dependências) com comportamentos distintos:

| Serviço | Porta | Modo | Comportamento |
|---|---|---|---|
| `demo-healthy` | 9101 | `healthy` | Responde `200` rápido |
| `demo-degraded` | 9102 | `degraded` | Responde `200` com ~1,8 s de latência |
| `demo-flaky` | 9103 | `flaky` | Alterna `200`/`503` conforme o painel de controle |

## Painel de controle (`/__control`)

| Chamada | Efeito |
|---|---|
| `GET /__control` | Estado atual |
| `POST /__control/down` `{"status":503}` | Passa a responder falha |
| `POST /__control/up` | Volta a responder sucesso |
| `POST /__control/latency` `{"ms":2500}` | Define latência artificial |
| `POST /__control/pattern` `{"pattern":"ok,ok,fail"}` | Sequência determinística repetida |
| `POST /__control/reset` | Restaura o modo padrão |

Use `scripts/demo.sh` ou `scripts/demo.ps1` para acionar os cenários sem digitar `curl`.
