package com.guest_platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

    @Test
    void productionCorsConfigurationAllowsOnlyTheHostveroOrigin() {
        CorsConfiguration configuration = SecurityConfig.corsConfiguration("https://app.hostvero.net");

        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.hostvero.net");
        assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
        assertThat(configuration.getAllowCredentials()).isFalse();
    }

    @Test
    void localDevelopmentCorsConfigurationAllowsOnlyLocalApplicationOrigins() {
        CorsConfiguration configuration = SecurityConfig.corsConfiguration("http://localhost:8080,http://127.0.0.1:8080");

        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:8080", "http://127.0.0.1:8080");
    }
}
