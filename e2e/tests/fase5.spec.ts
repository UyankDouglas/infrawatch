import { expect, test } from "@playwright/test";

/**
 * Fase 5 — relatórios, exportação, convites e canais externos, contra o ambiente completo (seed ativa).
 */
test.describe.serial("Fase 5 — relatórios, exportações, convites e integrações", () => {
  const stamp = Date.now();

  test("relatório de disponibilidade com fórmula explícita e exportação CSV no histórico", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("E-mail").fill("admin@demo.infrawatch.local");
    await page.getByLabel("Senha").fill("InfraWatch@2026");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByRole("heading", { name: "Visão Geral" })).toBeVisible();

    await page.getByRole("link", { name: "Relatórios" }).click();
    await expect(page.getByRole("heading", { name: "Relatórios" })).toBeVisible();
    await expect(page.getByText(/tempo disponível ÷ tempo observado/)).toBeVisible();
    await expect(page.getByText(/EXCLUÍDOS do tempo observado/)).toBeVisible();
    const table = page.getByRole("tabpanel").getByRole("table");
    await expect(table.getByText("API Saudável")).toBeVisible();

    await page.getByRole("button", { name: "Exportar disponibilidade" }).click();
    await page.getByRole("tab", { name: "Exportações" }).click();
    const history = page.getByRole("tabpanel");
    await expect(history.getByText(/infrawatch-availability-7d-.*\.csv/)).toBeVisible();
    await expect(history.getByRole("link", { name: /Baixar/ }).first()).toHaveAttribute("href", /\/reports\/exports\/.*\/download/);
  });

  test("convite: link exibido uma vez; cadastro pelo convite entra na organização existente", async ({ page, browser }) => {
    await page.goto("/login");
    await page.getByLabel("E-mail").fill("admin@demo.infrawatch.local");
    await page.getByLabel("Senha").fill("InfraWatch@2026");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByRole("heading", { name: "Visão Geral" })).toBeVisible();

    await page.goto("/configuracoes");
    const email = `convidado-${stamp}@teste.local`;
    await page.getByLabel("E-mail do convidado").fill(email);
    await page.getByRole("button", { name: "Convidar" }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByText("Convite criado")).toBeVisible();
    const link = await dialog.getByLabel(/Link do convite/).inputValue();
    expect(link).toMatch(/\/convite\/inv_/);

    const guest = await browser.newContext();
    const guestPage = await guest.newPage();
    await guestPage.goto(new URL(link).pathname);
    await expect(guestPage.getByRole("heading", { name: /Convite para Demo Corp/ })).toBeVisible();
    await guestPage.getByRole("link", { name: "Criar conta e entrar na organização" }).click();
    await guestPage.getByLabel("Nome completo").fill("Pessoa Convidada");
    await guestPage.getByLabel("Senha (mínimo 10 caracteres)").fill("SenhaForte#2026");
    await guestPage.getByLabel("Confirmar senha").fill("SenhaForte#2026");
    await guestPage.getByRole("button", { name: "Criar conta e aceitar convite" }).click();
    await expect(guestPage.getByRole("heading", { name: "Visão Geral" })).toBeVisible();
    await expect(guestPage.getByText("Demo Corp").first()).toBeVisible();
    await guest.close();
  });

  test("canal webhook aceita destino da allowlist, recusa destino externo e registra a entrega de teste", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("E-mail").fill("admin@demo.infrawatch.local");
    await page.getByLabel("Senha").fill("InfraWatch@2026");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByRole("heading", { name: "Visão Geral" })).toBeVisible();

    await page.goto("/configuracoes");
    await page.getByRole("tab", { name: "Integrações" }).click();
    await page.getByRole("button", { name: "Novo canal" }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Nome").fill(`Hook E2E ${stamp}`);
    await dialog.getByLabel("URL do webhook").fill("https://exemplo-externo.invalid/hook");
    await dialog.getByRole("button", { name: "Criar canal" }).click();
    await expect(page.getByText(/recusado pela política/).first()).toBeVisible();

    const target = process.env.E2E_DEMO_TARGET_HOST ?? "localhost";
    await dialog.getByLabel("URL do webhook").fill(`http://${target}:9101/__webhook`);
    await dialog.getByRole("button", { name: "Criar canal" }).click();
    await expect(dialog).toBeHidden();
    const row = page.getByRole("row", { name: new RegExp(`Hook E2E ${stamp}`) });
    await expect(row).toBeVisible();
    await row.getByRole("button", { name: "Testar" }).click();
    // Com o envio externo desativado (padrão) a entrega fica "Ignorada"; habilitado, fica "Enviada".
    await expect(page.getByRole("cell", { name: /Enviada|Ignorada/ }).first()).toBeVisible();
  });
});
