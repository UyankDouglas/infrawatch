package com.infrawatch.organization;

/** Perfis de acesso dentro de uma organização. A ordem define a hierarquia (ADMIN ⊃ OPERATOR ⊃ VIEWER). */
public enum MemberRole {
    VIEWER(1, "Visualizador"),
    OPERATOR(2, "Operador"),
    ADMIN(3, "Administrador");

    private final int level;
    private final String label;

    MemberRole(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public String getLabel() { return label; }

    public boolean atLeast(MemberRole other) {
        return this.level >= other.level;
    }
}
