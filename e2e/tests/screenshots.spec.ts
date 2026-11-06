import { expect, test } from "@playwright/test";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Gera as capturas de tela de docs/screenshots a partir do ambiente de demonstração real (seed ativa).
 * Executar: cd e2e && SCREENSHOTS=1 npx playwright test tests/screenshots.spec.ts --project=chromium
 * Fica fora da suíte padrão (só roda com SCREENSHOTS=1) para não depender de dados visuais no CI.
 */
test.skip(!process.env.SCREENSHOTS, "defina SCREENSHOTS=1 para gerar as capturas");

const OUT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../docs/screenshots");
const shot = (name: string) => path.join(OUT, `${name}.png`);

test.use({ viewport: { width: 1440, height: 900 }, colorScheme: "dark" });
test.setTimeout(240_000);

test.describe.serial("capturas de tela", () => {
  test.beforeAll(() => mkdirSync(OUT, { recursive: true }));

  test("páginas principais", async ({ page }) => {
    await page.goto("/login");
    await page.screenshot({ path: shot("01-login"), fullPage: false });
    await page.getByLabel("E-mail").fill("admin@demo.infrawatch.local");
    await page.getByLabel("Senha").fill("InfraWatch@2026");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByRole("heading", { name: "Visão Geral" })).toBeVisible();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: shot("02-visao-geral"), fullPage: true });

    await page.goto("/recursos");
    await expect(page.getByRole("heading", { name: "Recursos Monitorados" })).toBeVisible();
    await page.waitForTimeout(800);
    await page.screenshot({ path: shot("03-recursos"), fullPage: true });

    await page.getByRole("link", { name: /API Degradada/ }).first().click();
    await expect(page.getByRole("heading", { name: "API Degradada" })).toBeVisible();
    await page.waitForTimeout(1200);
    await page.screenshot({ path: shot("04-recurso-detalhe"), fullPage: true });

    await page.goto("/alertas");
    await expect(page.getByRole("heading", { name: "Alertas" })).toBeVisible();
    await page.waitForTimeout(800);
    await page.screenshot({ path: shot("05-alertas"), fullPage: true });

    await page.goto("/incidentes");
    await expect(page.getByRole("heading", { name: "Incidentes" })).toBeVisible();
    await page.waitForTimeout(800);
    await page.screenshot({ path: shot("06-incidentes"), fullPage: true });

    await page.goto("/manutencoes");
    await expect(page.getByRole("heading", { name: "Manutenções" })).toBeVisible();
    await page.waitForTimeout(800);
    await page.screenshot({ path: shot("07-manutencoes"), fullPage: true });

    await page.goto("/relatorios");
    await expect(page.getByRole("heading", { name: "Relatórios" })).toBeVisible();
    await page.waitForTimeout(1200);
    await page.screenshot({ path: shot("08-relatorios"), fullPage: true });

    await page.goto("/configuracoes");
    await page.getByRole("tab", { name: "Integrações" }).click();
    await page.waitForTimeout(800);
    await page.screenshot({ path: shot("09-integracoes"), fullPage: true });

    await page.getByRole("tab", { name: "Membros" }).click();
    await page.waitForTimeout(500);
    await page.screenshot({ path: shot("10-membros"), fullPage: true });

    // tema claro
    await page.emulateMedia({ colorScheme: "light" });
    await page.goto("/");
    await page.getByRole("button", { name: /Ativar modo claro/ }).click({ timeout: 5_000 });
    await page.waitForTimeout(1200);
    await page.screenshot({ path: shot("11-visao-geral-claro"), fullPage: false });
  });
});
