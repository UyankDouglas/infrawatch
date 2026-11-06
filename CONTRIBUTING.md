# Contribuindo

## Ambiente

- JDK 21, Node 22, Docker Desktop. Postgres e serviços de demonstração: `docker compose up -d postgres demo-healthy demo-degraded demo-flaky`.
- API: `cd apps/api && INFRAWATCH_SEED_ENABLED=true ./mvnw spring-boot:run` · Web: `cd apps/web && npm install && npm run dev`.
- Antes de abrir um PR: `./mvnw test` (apps/api), `npm run lint && npm run typecheck && npm run test:run` (apps/web) e,
  se mexeu em fluxo de tela, o E2E contra `docker compose --profile full up -d --build`.

## Regras que não mudam

1. **Nada de mocks permanentes**: verificações, métricas e entregas são reais ou não existem.
2. **Nunca um scanner**: todo destino (verificação ou webhook) passa pelo `TargetValidator`; não adicione modos que o contornem.
3. **Isolamento por organização**: toda consulta filtra por `organization_id` no repositório; toda rota nova com id entra na
   lista de `IsolationSweepIT`.
4. **Sem dados ≠ falha**: ausência de coleta nunca vira Indisponível nem conta como indisponibilidade.
5. **Rótulos em pt-BR, códigos internos estáveis** (enums/`code` de erro não mudam de nome).
6. **Nada sensível persistido**: sem cookies, tokens em claro ou corpos de resposta monitorada.
7. **Decisões documentadas**: mudanças de arquitetura entram em `docs/decisions.md`; esquema só por migração Flyway nova
   (nunca edite uma migração já aplicada).

## Convenções

- Commits no estilo `tipo: descrição` (`feat`, `fix`, `test`, `docs`, `ci`, `chore`).
- Backend: pacotes por domínio (`controller` → `service` → `repository`), DTOs em `*Dtos.java`, erros via `ApiException`.
- Frontend: páginas em `src/pages`, blocos reutilizáveis em `src/features/<domínio>`, chamadas em `src/services/endpoints.ts`, tipos em `src/types`.
- Formatação: Prettier (web) e estilo padrão do IntelliJ/Google para Java (4 espaços, 120 colunas).

## Relatar problemas de segurança

Abra uma issue privada ou escreva ao mantenedor antes de divulgar. Inclua passos para reproduzir e a versão.
