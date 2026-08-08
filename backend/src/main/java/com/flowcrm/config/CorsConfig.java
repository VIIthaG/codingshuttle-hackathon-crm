package com.flowcrm.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Explicit CORS source bean for Spring Security. Origins come from
 * {@code CORS_ALLOWED_ORIGINS} (via {@link CorsProperties}).
 */
@Configuration
public class CorsConfig {

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.resolvedAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
