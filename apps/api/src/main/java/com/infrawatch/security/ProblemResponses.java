package com.infrawatch.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Escreve respostas RFC 9457 a partir de filtros, onde o ExceptionHandler do MVC não alcança. */
final class ProblemResponses {
    private ProblemResponses() {}

    static void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"type\":\"https://infrawatch.dev/errors/" + code + "\",\"title\":\"" + code
                + "\",\"status\":" + status + ",\"detail\":\"" + escape(detail) + "\",\"code\":\"" + code + "\"}";
        response.getWriter().write(body);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
