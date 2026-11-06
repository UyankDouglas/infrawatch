package com.infrawatch.resource;

public enum ResourceType {
    HTTP("Serviço HTTP", true),
    HTTPS("Serviço HTTPS", true),
    HEALTH_ENDPOINT("Aplicação com health check", true),
    AGENT_SERVER("Servidor com agente autorizado", false);

    private final String label;
    private final boolean httpCheck;

    ResourceType(String label, boolean httpCheck) {
        this.label = label;
        this.httpCheck = httpCheck;
    }

    public String getLabel() { return label; }

    /** Indica se o tipo é verificado pelo motor HTTP (os demais dependem de agente — Fase 2). */
    public boolean isHttpCheck() { return httpCheck; }
}
