package com.infrawatch.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrawatch.security")
public record SecurityProperties(List<String> corsAllowedOrigins, RateLimit rateLimit) {

    public SecurityProperties {
        if (corsAllowedOrigins == null) corsAllowedOrigins = List.of("http://localhost:5174");
        if (rateLimit == null) rateLimit = new RateLimit(20);
    }

    public record RateLimit(int authRequestsPerMinute) {
        public RateLimit {
            if (authRequestsPerMinute <= 0) authRequestsPerMinute = 20;
        }
    }
}
