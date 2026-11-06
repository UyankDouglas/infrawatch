package com.infrawatch.alerts;

public final class AlertEnums {
    private AlertEnums() {}

    /** Métrica/condição avaliada. Só existem regras para métricas que o sistema de fato coleta. */
    public enum Metric {
        STATUS_DOWN("Serviço indisponível", false, "status"),
        STATUS_DEGRADED("Serviço degradado", false, "status"),
        LATENCY("Latência acima do limite", true, "ms"),
        CPU("CPU acima do limite", true, "%"),
        MEMORY("Memória acima do limite", true, "%"),
        DISK("Disco com pouco espaço", true, "%"),
        AGENT_NO_DATA("Falta de dados do agente", false, "status");

        private final String label;
        private final boolean numeric;
        private final String unit;

        Metric(String label, boolean numeric, String unit) {
            this.label = label;
            this.numeric = numeric;
            this.unit = unit;
        }

        public String getLabel() { return label; }
        public boolean isNumeric() { return numeric; }
        public String getUnit() { return unit; }
    }

    public enum Operator {
        GT(">"), GTE("≥"), LT("<"), LTE("≤");

        private final String symbol;
        Operator(String symbol) { this.symbol = symbol; }
        public String getSymbol() { return symbol; }

        public boolean test(java.math.BigDecimal value, java.math.BigDecimal threshold) {
            int c = value.compareTo(threshold);
            return switch (this) {
                case GT -> c > 0;
                case GTE -> c >= 0;
                case LT -> c < 0;
                case LTE -> c <= 0;
            };
        }
    }

    public enum Severity {
        INFO("Informativo", 1), WARNING("Atenção", 2), HIGH("Alto", 3), CRITICAL("Crítico", 4);

        private final String label;
        private final int rank;
        Severity(String label, int rank) { this.label = label; this.rank = rank; }
        public String getLabel() { return label; }
        public int getRank() { return rank; }
    }

    public enum Status {
        OPEN("Aberto"), ACKNOWLEDGED("Reconhecido"), RESOLVED("Resolvido");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Resolution { CONDITION_CLEARED, MANUAL, RULE_DISABLED, RESOURCE_REMOVED }
}
