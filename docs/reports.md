# Relatórios e exportações (Fase 5)

Todos os números vêm das tabelas de domínio (`resource_status_history`, `health_check_executions`, `incidents`,
`metric_samples`/`metric_rollups`). Nada é estimado: quando não há medição, o relatório diz **sem dados**.

## Definições

| Indicador | Definição | Limitação declarada |
|---|---|---|
| **Disponibilidade (%)** | tempo disponível ÷ tempo observado × 100. Tempo observado = intervalos com estado conhecido (Disponível, Degradado, Indisponível). *Degradado* conta como disponível. *Aguardando* e *Sem dados* nunca entram no cálculo. | Recurso criado dentro do período só é observado a partir da criação. |
| **Tratamento de manutenções** | `excluded` (padrão): intervalos em *Em manutenção* saem do tempo observado. `counted`: contam como indisponibilidade. | O relatório, a exportação e a interface sempre mostram qual modo foi usado. |
| **Tempo indisponível** | soma dos intervalos em *Indisponível* dentro da janela. | — |
| **Tempo em manutenção** | soma dos intervalos em *Em manutenção* dentro da janela (informativo nos dois modos). | — |
| **Verificações / falhas** | contagem de `health_check_executions` na janela. | — |
| **Latência média / p95 / máxima** | sobre verificações **bem-sucedidas** (`percentile_cont(0.95)` no PostgreSQL). | Falhas não poluem a latência. |
| **Incidentes** | incidentes abertos na janela (automáticos + manuais). | — |
| **MTTR** | média de (`resolved_at` − `opened_at`) dos incidentes **resolvidos** na janela. | `null` quando nenhum foi resolvido. |
| **MTTA** | média de (`acknowledged_at` − `opened_at`) dos incidentes reconhecidos. | — |
| **MTBF** | média do intervalo entre o fim de um incidente e o início do seguinte, por recurso, na janela. | Exige **≥ 2 incidentes**; caso contrário o relatório traz a nota "MTBF exige ao menos 2 incidentes no período". Incidentes sobrepostos não geram intervalo. |

## Endpoints

| Rota | Descrição |
|---|---|
| `GET /api/v1/reports/availability?period=7d&maintenance=excluded&groupId=` | uma linha por recurso + totais + `formula` em texto |
| `GET /api/v1/reports/incidents?period=7d&resourceId=` | lista com duração, tempo até reconhecer, causa; contagens por severidade/origem; MTTR/MTTA |
| `GET /api/v1/reports/resources/{id}?period=&maintenance=` | resumo do recurso, incidentes, série de latência e evolução de CPU/memória/disco (brutas ≤ 6 h, rollup 5 min ≤ 7 d, rollup 1 h acima) |
| `POST /api/v1/reports/exports` `{type: AVAILABILITY\|INCIDENTS\|CHECKS, format: CSV\|XLSX, period, maintenance, groupId}` | gera e armazena o arquivo |
| `GET /api/v1/reports/exports` | histórico (últimas 50 da organização: tipo, período, modo, linhas, tamanho, autor) |
| `GET /api/v1/reports/exports/{id}/download` | download com `Content-Disposition: attachment` |

Períodos aceitos: `1h`, `6h`, `24h`, `7d`, `30d`. Qualquer membro (inclusive Visualizador) pode consultar e exportar;
o isolamento por organização vale para o histórico e o download (404 para exportações de outra organização).

## Exportações

- **CSV**: UTF-8 com BOM (Excel abre acentos corretamente), separador `;`, decimais com vírgula, datas em
  `America/Sao_Paulo`. Células que começam com `=`, `+`, `-`, `@`, tab ou CR são prefixadas com `'` (proteção
  contra *CSV injection*), exceto números.
- **XLSX**: Apache POI (`XSSFWorkbook`), cabeçalho em negrito, primeira linha congelada, números como números.
- O conteúdo fica em `report_exports.content` (`bytea`), com `size_bytes`, `row_count`, filtros e autor — permite
  auditar o que foi exportado e baixar de novo sem recalcular.
- A exportação de verificações é limitada às **20 000** execuções mais recentes da janela.
- Cada exportação gera `report.exported` na auditoria.

## Interface

**Relatórios** (menu lateral): filtros de período, tratamento de manutenções e grupo; KPIs (disponibilidade geral,
tempo indisponível, incidentes, MTTR/MTTA); aba *Disponibilidade* (gráfico de barras + tabela com latências, MTTR, MTBF
e dica explicando quando o MTBF não se aplica), aba *Incidentes* (distribuição por severidade + tabela), aba
*Exportações* (histórico e download). A página do recurso ganhou a aba **Relatório** com os mesmos indicadores e as
curvas de latência e de CPU/memória/disco.
