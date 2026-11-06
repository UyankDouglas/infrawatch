package com.infrawatch.monitoring;

import java.util.ArrayList;
import java.util.List;

/** Interpreta expressões como "200-299,301,404" para decidir quais códigos HTTP representam sucesso. */
public final class StatusCodeMatcher {

    private record Range(int from, int to) {
        boolean contains(int v) { return v >= from && v <= to; }
    }

    private final List<Range> ranges = new ArrayList<>();

    public StatusCodeMatcher(String expression) {
        String expr = expression == null || expression.isBlank() ? "200-299" : expression;
        for (String part : expr.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] bounds = p.split("-");
            try {
                if (bounds.length == 1) {
                    int v = Integer.parseInt(bounds[0].trim());
                    ranges.add(new Range(v, v));
                } else {
                    int a = Integer.parseInt(bounds[0].trim());
                    int b = Integer.parseInt(bounds[1].trim());
                    ranges.add(new Range(Math.min(a, b), Math.max(a, b)));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expressão de códigos HTTP inválida: " + expression);
            }
        }
        if (ranges.isEmpty()) ranges.add(new Range(200, 299));
    }

    public boolean matches(int status) {
        return ranges.stream().anyMatch(r -> r.contains(status));
    }
}
