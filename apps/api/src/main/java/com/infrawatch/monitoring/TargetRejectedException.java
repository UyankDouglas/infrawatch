package com.infrawatch.monitoring;

/** Destino recusado pela política de alvos (sintaxe, allowlist, porta, rede interna etc.). */
public class TargetRejectedException extends RuntimeException {
    private final String code;

    public TargetRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
