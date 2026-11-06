package com.infrawatch.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de frequência simples (janela fixa de 1 minuto por IP) para endpoints de autenticação.
 * Em memória: suficiente para uma instância. Para múltiplas instâncias, substituir por Redis (Fase 5).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record Window(long minute, AtomicInteger count) {}

    private final int limitPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/forgot-password") || path.equals("/api/v1/auth/reset-password"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = clock.millis() / 60_000L;
        String key = clientIp(request);
        Window window = windows.compute(key, (k, existing) ->
                (existing == null || existing.minute() != minute) ? new Window(minute, new AtomicInteger()) : existing);
        if (window.count().incrementAndGet() > limitPerMinute) {
            response.setHeader("Retry-After", "60");
            ProblemResponses.write(response, 429, "rate_limited",
                    "Muitas tentativas. Aguarde um minuto e tente novamente.");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }
}
