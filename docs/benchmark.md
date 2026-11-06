# Benchmark (Fase 6)

Benchmark reproduzível, fora da suíte padrão:

```bash
cd apps/api && ./mvnw test -Dtest=BenchmarkIT -Dinfrawatch.bench=true
```

Parâmetros ajustáveis: `-Dbench.resources=300 -Dbench.executions=200000 -Dbench.transitions=20000 -Dbench.incidents=2000`.
O teste cria uma organização, insere o volume sintético por JDBC em lote (`ANALYZE` ao final), aquece cada operação uma
vez e mede 10–30 execuções (p50/p95/máx em ms). Resultado em `target/benchmark.md`.

## Ambiente de referência

Windows 10, PostgreSQL 17 em container (Testcontainers), API dentro da mesma JVM de teste (MockMvc, sem rede), 2026-11-06.
Números servem para comparação relativa e detecção de regressões grosseiras, não como SLA.

## Seed

300 recursos HTTP, 200 000 execuções de verificação em 30 dias (3 % de falhas), 20 000 transições de estado,
2 000 incidentes resolvidos. Seed em ~65 s.

## Resultados

### Antes (commit da Fase 5)

| Operação | p50 (ms) | p95 (ms) |
|---|---:|---:|
| `claimDue(50)` com 300 recursos vencidos | 16,0 | 20,3 |
| `GET /reports/availability?period=30d` | 943,6 | 984,2 |
| `GET /reports/availability?period=7d` | 604,2 | 664,5 |
| `GET /reports/incidents?period=30d` | 573,6 | 611,1 |
| `GET /dashboard/overview?period=24h` | 534,6 | 631,1 |
| `GET /dashboard/availability?period=7d` | 73,2 | 89,2 |
| `GET /resources?page=0&size=50` | 90,6 | 97,3 |
| `GET /resources/{id}/metrics?period=30d` | 25,0 | 28,7 |
| `GET /reports/resources/{id}?period=30d` | 860,5 | 962,2 |

### Depois (correção do N+1)

| Operação | p50 (ms) | p95 (ms) | variação p50 |
|---|---:|---:|---:|
| `claimDue(50)` com 300 recursos vencidos | 15,7 | 17,6 | = |
| `GET /reports/availability?period=30d` | 451,3 | 648,8 | −52 % |
| `GET /reports/availability?period=7d` | 147,7 | 161,9 | −76 % |
| `GET /reports/incidents?period=30d` | 116,4 | 124,7 | −80 % |
| `GET /dashboard/overview?period=24h` | 84,1 | 94,8 | −84 % |
| `GET /dashboard/availability?period=7d` | 74,8 | 82,5 | = |
| `GET /resources?page=0&size=50` | 76,8 | 88,2 | −15 % |
| `GET /resources/{id}/metrics?period=30d` | 25,1 | 35,6 | = |
| `GET /reports/resources/{id}?period=30d` | 571,8 | 643,3 | −34 % |

## O que o benchmark encontrou

Carregar centenas de `MonitoredResource` disparava uma consulta por recurso para as **tags** (`@ElementCollection` EAGER)
e outra para a **configuração de verificação** (`@OneToOne(mappedBy)` EAGER): ~600 consultas por chamada do dashboard
ou do relatório. Correção (decisão 39):

- `@BatchSize(100)` nas tags → 3 consultas por 300 recursos;
- `@EntityGraph(attributePaths = {"group", "checkConfig"})` na consulta em lote → grupo e configuração no mesmo `SELECT`;
- relatórios de incidentes e dashboard passaram a buscar apenas `id → nome` por projeção em vez de carregar entidades.

## Leitura dos números restantes

- **Claim do despachante** (~16 ms para 50 de 300 vencidos) é dominado pelo `UPDATE … FOR UPDATE SKIP LOCKED`; com
  intervalo mínimo de 30 s, 300 recursos geram 10 claims/s — folga de duas ordens de grandeza.
- **Disponibilidade 30 d** (~450 ms) é dominada por `percentile_cont(0.95)` e agregações sobre 200 k execuções
  (índice `(organization_id, started_at)` em uso). Aceitável para uma tela de relatório; se o volume crescer uma ordem
  de grandeza, o caminho é materializar agregados por hora (decisão 31) — a retenção padrão de execuções (30 d) limita
  o crescimento.
- **Relatório do recurso 30 d** soma o relatório de disponibilidade de toda a organização (reuso deliberado do cálculo)
  mais a série de latência por bucket; uma otimização simples seria calcular só o recurso pedido.

## Como interpretar regressões

Rode o benchmark antes e depois de mudanças em consultas, mapeamentos JPA ou relatórios e compare o p50. O teste falha
apenas se algum p95 passar de 5 s (guarda contra regressões grosseiras), por não fazer sentido fixar números absolutos
em máquinas diferentes.
