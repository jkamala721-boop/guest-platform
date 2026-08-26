package com.guest_platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    @Test
    void adminCorsAllowsOnlyTheDedicatedCredentialedManagementOrigin() {
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource)
                new SecurityConfig().adminCorsConfigurationSource("https://admin.hostvero.net");
        CorsConfiguration configuration = source.getCorsConfigurations().get("/api/admin/**");

        assertThat(configuration.getAllowedOrigins()).containsExactly("https://admin.hostvero.net");
        assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "POST", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Content-Type");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
