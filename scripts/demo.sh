#!/usr/bin/env bash
# InfraWatch — controle determinístico dos serviços de demonstração.
# Uso: scripts/demo.sh <comando> [args]
#   status                     Mostra o estado dos três serviços
#   fail    [porta]            Faz o serviço responder 503 (padrão: 9103, API Instável)
#   recover [porta]            Volta a responder 200
#   slow    <ms> [porta]       Define latência artificial (padrão: 9102, API Degradada)
#   pattern "ok,ok,fail" [porta]  Sequência determinística de respostas
#   reset                      Restaura todos os serviços ao padrão
set -euo pipefail
HOST="${DEMO_HOST:-localhost}"
cmd="${1:-status}"
ctl() { curl -sS -X POST "http://$HOST:$1/__control/$2" -H "Content-Type: application/json" -d "${3:-{\}}" ; echo; }
case "$cmd" in
  status)  for p in 9101 9102 9103; do printf "%s: " "$p"; curl -sS "http://$HOST:$p/__control"; echo; done ;;
  fail)    ctl "${2:-9103}" down '{"status":503}' ;;
  recover) ctl "${2:-9103}" up ;;
  slow)    ctl "${3:-9102}" latency "{\"ms\":${2:?informe os ms}}" ;;
  pattern) ctl "${3:-9103}" pattern "{\"pattern\":\"${2:?informe o padrão}\"}" ;;
  reset)   for p in 9101 9102 9103; do ctl "$p" reset; done ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
