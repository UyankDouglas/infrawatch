# Alertas, incidentes e disponibilidade

> Alertas (Fase 3) e incidentes/manutenções (Fase 4) ainda não foram implementados. Este documento fixa as definições que o restante do sistema já respeita.

## Disponibilidade

```
Disponibilidade (%) = tempo disponível / tempo observado × 100
```

- **Tempo observado**: soma dos intervalos em que o estado era **conhecido** (`UP`, `DEGRADED`, `DOWN`).
- **Tempo disponível**: intervalos em `UP` ou `DEGRADED` (o serviço responde, ainda que lento).
- `PENDING`, `UNKNOWN` e `MAINTENANCE` são **excluídos** do tempo observado: não contam a favor nem contra.
- Sem nenhum intervalo conhecido, o resultado é **indefinido** (`null`) e a interface mostra "Sem dados suficientes" — nunca 100%.
- A janela de um recurso começa, no máximo, na sua criação.
- O cálculo é feito a partir de `resource_status_history` (`AvailabilityCalculator`), não de contagem de verificações.

Na Fase 4, os relatórios indicarão se a disponibilidade **inclui ou exclui** janelas de manutenção (padrão: exclui, como `MAINTENANCE` já é tratado hoje).

O gráfico "Disponibilidade por período" da Visão Geral usa outra métrica, explicitada na legenda: **percentual de verificações bem-sucedidas por intervalo** (baseada em `health_check_executions`). As duas convivem porque respondem a perguntas diferentes (tempo em estado × taxa de sucesso das sondagens).

## Alertas (Fase 3 — definições)

- Condições: indisponível, recuperado, latência acima do limite, CPU/RAM/disco acima do limite (apenas quando houver agente), falta de dados do agente.
- Severidades: Informativo, Atenção, Alto, Crítico.
- Um alerta permanece **ativo** enquanto a condição persistir; novas execuções não criam alertas duplicados. Reconhecimento e resolução são registrados com usuário e horário.
- Histerese reaproveita a máquina de estados: alertas de disponibilidade são disparados pelas **transições**, não por verificações isoladas.

## Incidentes (Fase 4 — definições)

- Abertura automática na transição para `DOWN` (configurável por recurso/grupo) ou manual.
- Status: Aberto → Reconhecido → Em investigação → Resolvido → Encerrado.
- **MTTR** = média de (resolução − abertura) dos incidentes resolvidos no período.
- **MTBF** = média do tempo entre o fim de um incidente e o início do seguinte, por recurso; informado apenas quando houver ≥ 2 incidentes no período, com a limitação explícita.
- Incidente automático é resolvido quando o recurso volta a `UP`/`DEGRADED` (regra configurável); incidentes manuais nunca são encerrados automaticamente.
- A timeline preserva os eventos reais (transições, comentários, atribuições) — nada é reescrito.

## Manutenções (Fase 4 — definições)

- Durante a janela o recurso exibe `MAINTENANCE`; as verificações continuam (para registrar latência), mas transições não geram alertas/incidentes.
- Ao encerrar, o estado é recalculado pela próxima verificação (sem herdar falhas da janela).
