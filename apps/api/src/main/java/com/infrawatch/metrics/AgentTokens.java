package com.infrawatch.metrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Tokens de agente no formato {@code iwa_<prefixo>.<segredo>}.
 * O prefixo (público) localiza a credencial; o servidor guarda apenas SHA-256 do token completo.
 */
public final class AgentTokens {

    public static final String PREFIX = "iwa_";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public record Generated(String token, String prefix, String hash) {}

    private AgentTokens() {}

    public static Generated generate() {
        byte[] prefixBytes = new byte[6];
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(prefixBytes);
        RANDOM.nextBytes(secretBytes);
        String prefix = B64.encodeToString(prefixBytes); // 8 chars
        String token = PREFIX + prefix + "." + B64.encodeToString(secretBytes);
        return new Generated(token, prefix, hash(token));
    }

    /** Constrói um token determinístico a partir de um valor fornecido (usado apenas pela seed de demonstração). */
    public static Generated fromRaw(String rawToken) {
        return new Generated(rawToken, prefixOf(rawToken).orElseThrow(() -> new IllegalArgumentException(
                "Token de agente inválido: use o formato iwa_<prefixo>.<segredo>")), hash(rawToken));
    }

    public static Optional<String> prefixOf(String token) {
        if (token == null || !token.startsWith(PREFIX)) return Optional.empty();
        int dot = token.indexOf('.', PREFIX.length());
        if (dot <= PREFIX.length() || dot >= token.length() - 1) return Optional.empty();
        String prefix = token.substring(PREFIX.length(), dot);
        return prefix.length() > 16 ? Optional.empty() : Optional.of(prefix);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String token, String expectedHash) {
        return MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
