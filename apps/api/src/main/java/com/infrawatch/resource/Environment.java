package com.infrawatch.resource;

public enum Environment {
    DEVELOPMENT("Desenvolvimento"),
    STAGING("Homologação"),
    PRODUCTION("Produção");

    private final String label;

    Environment(String label) { this.label = label; }

    public String getLabel() { return label; }
}
