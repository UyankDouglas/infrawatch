package com.infrawatch.resource;

/**
 * Códigos internos estáveis com rótulos em português.
 * PENDING e UNKNOWN representam ausência de coleta, nunca falha comprovada.
 */
public enum ResourceStatus {
    PENDING("Aguardando verificação", false),
    UP("Disponível", true),
    DOWN("Indisponível", true),
    DEGRADED("Degradado", true),
    MAINTENANCE("Em manutenção", false),
    UNKNOWN("Sem dados", false);

    private final String label;
    private final boolean known;

    ResourceStatus(String label, boolean known) {
        this.label = label;
        this.known = known;
    }

    public String getLabel() { return label; }

    /** Estados "conhecidos" entram no cálculo de disponibilidade; os demais são excluídos do tempo observado. */
    public boolean isKnown() { return known; }

    public boolean isAvailable() { return this == UP || this == DEGRADED; }
}
