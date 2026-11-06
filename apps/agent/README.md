# InfraWatch Agent

Agente leve e **opcional** para servidores administrados pelo usuário. Coleta somente métricas permitidas e as envia ao InfraWatch; **não abre portas, não executa comandos e não dá acesso remoto ao sistema operacional**.

| Métrica | Fonte | Quando indisponível |
|---|---|---|
| Uso de CPU (%) | `OperatingSystemMXBean.getCpuLoad()` (ciente de cgroups) | `null` |
| Carga média 1 min | `getSystemLoadAverage()` | `null` (Windows) |
| Memória total/usada | `getTotalMemorySize()` / `getFreeMemorySize()` | `null` |
| Disco total/usado | `FileStore` do caminho configurado | `null` |
| Uptime do sistema | `/proc/uptime` (Linux) | `null` em outros SOs |

## Configuração (variáveis de ambiente)

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `INFRAWATCH_API_URL` | sim | — | ex.: `https://infrawatch.exemplo.com` ou `http://api:8080` |
| `INFRAWATCH_AGENT_TOKEN` | sim | — | token `iwa_…` gerado no painel (exibido uma única vez) |
| `INFRAWATCH_AGENT_INTERVAL` | não | 30 | segundos entre amostras (o servidor pode sugerir outro) |
| `INFRAWATCH_AGENT_DISK_PATH` | não | `/` | ponto de montagem monitorado |
| `INFRAWATCH_AGENT_TIMEOUT_MS` | não | 5000 | timeout por envio |
| `INFRAWATCH_AGENT_HOSTNAME` | não | hostname do SO | identificação exibida no painel |

## Executar

```bash
docker run -d --name infrawatch-agent \
  -e INFRAWATCH_API_URL=https://infrawatch.exemplo.com \
  -e INFRAWATCH_AGENT_TOKEN=iwa_xxxxxxxx.yyyyyyyy \
  infrawatch-agent
```

Ou como JAR: `java -jar infrawatch-agent.jar` com as mesmas variáveis. Build: `mvn -B package` (sem dependências externas).

## Segurança

- Autenticação por token Bearer; o servidor guarda apenas o hash SHA-256 e permite rotação/revogação.
- Use HTTPS fora de redes locais (o agente avisa quando a URL não é TLS).
- O servidor recusa amostras com relógio muito defasado e limita a frequência por agente.
