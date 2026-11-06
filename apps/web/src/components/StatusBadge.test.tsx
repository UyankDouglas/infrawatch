import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("exibe o rótulo em português de cada código interno", () => {
    render(
      <>
        <StatusBadge status="UP" />
        <StatusBadge status="DOWN" />
        <StatusBadge status="DEGRADED" />
        <StatusBadge status="PENDING" />
        <StatusBadge status="UNKNOWN" />
        <StatusBadge status="MAINTENANCE" />
      </>,
    );
    expect(screen.getByText("Disponível")).toBeInTheDocument();
    expect(screen.getByText("Indisponível")).toBeInTheDocument();
    expect(screen.getByText("Degradado")).toBeInTheDocument();
    expect(screen.getByText("Aguardando verificação")).toBeInTheDocument();
    expect(screen.getByText("Sem dados")).toBeInTheDocument();
    expect(screen.getByText("Em manutenção")).toBeInTheDocument();
  });

  it("aceita rótulo customizado e marca o status no DOM", () => {
    render(<StatusBadge status="DOWN" label="Fora do ar" />);
    const badge = screen.getByText("Fora do ar");
    expect(badge).toHaveAttribute("data-status", "DOWN");
  });
});
