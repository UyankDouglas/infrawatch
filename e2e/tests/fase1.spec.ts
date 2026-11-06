import { expect, test, type APIRequestContext } from "@playwright/test";

/**
 * Fluxo da Fase 1 contra o ambiente local (API + web + serviços de demonstração).
 * Pré-requisito: `docker compose --profile full up -d` (ou API em 8080 + Vite em 5174 + demos em 9101-9103).
 */
const DEMO_HOST = process.env.E2E_DEMO_HOST ?? "http://localhost";
const DEMO_TARGET_HOST = process.env.E2E_DEMO_TARGET_HOST ?? "localhost";

async function demoControl(request: APIRequestContext, port: number, action: string, data: unknown = {}) {
  const res = await request.post(`${DEMO_HOST}:${port}/__control/${action}`, { data });
  expect(res.ok()).toBeTruthy();
}

test.describe.serial("Fase 1 — cadastro, verificação real, falha controlada e recuperação", () => {
  const stamp = Date.now();
  const email = `e2e-${stamp}@teste.local`;
  const password = "SenhaForte#2026";
  const resourceName = `E2E Instável ${stamp}`;

  test.afterAll(async ({ request }) => {
    await demoControl(request, 9103, "up");
  });

  test("cria conta, cadastra serviço autorizado e vê a verificação real", async ({ page }) => {
    await page.goto("/cadastro");
    await page.getByLabel("Nome completo").fill("Pessoa E2E");
    await page.getByLabel("Nome da organização").fill(`Org E2E ${stamp}`);
    await page.getByLabel("E-mail").fill(email);
    await page.getByLabel("Senha (mínimo 10 caracteres)").fill(password);
    await page.getByLabel("Confirmar senha").fill(password);
    await page.getByRole("button", { name: "Criar conta e organização" }).click();

    await expect(page.getByRole("heading", { name: "Visão Geral" })).toBeVisible();
    await expect(page.getByText("Nenhum recurso monitorado ainda")).toBeVisible();

    await page.goto("/recursos?novo=1");
    await page.getByRole("dialog").getByLabel("Nome", { exact: true }).fill(resourceName);
    await page.getByRole("dialog").getByLabel("URL autorizada").fill(`http://${DEMO_TARGET_HOST}:9103/health`);
    await page.getByRole("button", { name: "Cadastrar e iniciar monitoramento" }).click();

    await expect(page.getByRole("heading", { name: resourceName })).toBeVisible();
    // Primeira verificação agendada pelo backend em poucos segundos.
    await expect(page.getByText("Disponível").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("HTTP 200")).toBeVisible();
  });

  test("falha controlada leva a Indisponível após 3 falhas e recuperação volta a Disponível", async ({ page, request }) => {
    await page.goto("/login");
    await page.getByLabel("E-mail").fill(email);
    await page.getByLabel("Senha", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByRole("heading", { name: "Visão Geral" })).toBeVisible();
    await page.goto("/recursos");
    await page.getByRole("link", { name: resourceName }).click();

    await demoControl(request, 9103, "down", { status: 503 });
    const runNow = page.getByRole("button", { name: "Verificar agora" });
    for (let i = 0; i < 3; i++) {
      await runNow.click();
      await page.waitForTimeout(800);
    }
    await expect(page.getByText("Indisponível").first()).toBeVisible();
    await expect(page.getByText("Estado alterado para Indisponível.")).toBeVisible();

    await demoControl(request, 9103, "up");
    await runNow.click();
    await page.waitForTimeout(800);
    await runNow.click();
    await expect(page.getByText("Estado alterado para Disponível.")).toBeVisible();

    await page.getByRole("tab", { name: "Histórico de estados" }).click();
    await expect(page.getByText("Recuperado após 2 sucesso(s) consecutivo(s)")).toBeVisible();
    await expect(page.getByText("3 falha(s) consecutiva(s)", { exact: false })).toBeVisible();

    await page.goto("/");
    await expect(page.getByText(resourceName).first()).toBeVisible();
  });

  test("usuário de outra organização não enxerga o recurso", async ({ page }) => {
    await page.goto("/cadastro");
    await page.getByLabel("Nome completo").fill("Outra Pessoa");
    await page.getByLabel("Nome da organização").fill(`Outra Org ${stamp}`);
    await page.getByLabel("E-mail").fill(`outra-${stamp}@teste.local`);
    await page.getByLabel("Senha (mínimo 10 caracteres)").fill(password);
    await page.getByLabel("Confirmar senha").fill(password);
    await page.getByRole("button", { name: "Criar conta e organização" }).click();
    await expect(page.getByRole("heading", { name: "Visão Geral" })).toBeVisible();
    await page.goto("/recursos");
    await expect(page.getByText("Nenhum recurso cadastrado")).toBeVisible();
    await expect(page.getByText(resourceName)).toHaveCount(0);
  });
});
