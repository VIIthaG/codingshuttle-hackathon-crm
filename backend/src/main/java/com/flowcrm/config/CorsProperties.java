package com.flowcrm.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Comma-separated browser origins allowed to call the API cross-origin.
     * Sourced from {@code CORS_ALLOWED_ORIGINS}; local default is {@code http://localhost:5173}.
     * Never use {@code *} in shared/hosted environments.
     */
    private String allowedOrigins = "http://localhost:5173";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> resolvedAllowedOrigins() {
        String raw = allowedOrigins == null ? "" : allowedOrigins;
        List<String> origins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> !"*".equals(origin))
                .toList();
        if (origins.isEmpty()) {
            return List.of("http://localhost:5173");
        }
        return origins;
    }
}
