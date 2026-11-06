import { describe, expect, it } from "vitest";
import { errorLabel, formatDuration, formatInterval, formatMs, formatPercent } from "./format";

describe("format", () => {
  it("formata milissegundos e segundos", () => {
    expect(formatMs(null)).toBe("—");
    expect(formatMs(42)).toBe("42 ms");
    expect(formatMs(1820)).toBe("1,82 s");
  });

  it("formata percentuais com vírgula decimal", () => {
    expect(formatPercent(null)).toBe("—");
    expect(formatPercent(99.954)).toBe("99,95%");
  });

  it("formata durações legíveis", () => {
    expect(formatDuration(45)).toBe("45 s");
    expect(formatDuration(600)).toBe("10 min");
    expect(formatDuration(3660)).toBe("1 h 1 min");
    expect(formatDuration(90000)).toBe("1 d 1 h");
  });

  it("formata intervalos de verificação", () => {
    expect(formatInterval(30)).toBe("30 s");
    expect(formatInterval(300)).toBe("5 min");
    expect(formatInterval(3600)).toBe("1 h");
  });

  it("traduz códigos de erro conhecidos e preserva desconhecidos", () => {
    expect(errorLabel("timeout")).toBe("Tempo limite");
    expect(errorLabel("algo_novo")).toBe("algo_novo");
    expect(errorLabel(null)).toBe("—");
  });
});
