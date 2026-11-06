package com.infrawatch.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {
    private Slugs() {}

    public static String of(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "org" : slug.substring(0, Math.min(60, slug.length()));
    }
}
