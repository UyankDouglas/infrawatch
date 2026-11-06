package com.infrawatch.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Sessões persistidas em PostgreSQL (Spring Session JDBC): sobrevivem a reinícios da API e permitem mais de uma instância.
 * O cookie é configurado explicitamente (nome, HttpOnly, SameSite=Lax, Secure por ambiente) porque o Spring Session
 * não herda as propriedades server.servlet.session.cookie.*.
 */
@Configuration
public class SessionConfig {

    public static final String COOKIE_NAME = "INFRAWATCH_SESSION";

    @Bean
    CookieSerializer cookieSerializer(@Value("${infrawatch.security.cookie-secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        serializer.setUseBase64Encoding(true);
        return serializer;
    }
}
