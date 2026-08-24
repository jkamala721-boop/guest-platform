package com.guest_platform.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.guest_platform.security.BearerSessionAuthenticationFilter;
import com.guest_platform.security.ApiErrorWriter;
import com.guest_platform.security.CookieCsrfProtectionFilter;
import com.guest_platform.security.PublicRateLimitFilter;
import com.guest_platform.service.HostSessionService;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(PublicRateLimitProperties.class)
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; form-action 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
            + "connect-src 'self'; frame-src https://checkout.paystack.com https://checkout.stripe.com";
    static final String PERMISSIONS_POLICY = "camera=(), geolocation=(), microphone=(), payment=(), usb=()";

    @Bean
    BearerSessionAuthenticationFilter bearerSessionAuthenticationFilter(HostSessionService hostSessionService,
            ApiErrorWriter apiErrorWriter) {
        return new BearerSessionAuthenticationFilter(hostSessionService, apiErrorWriter);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            BearerSessionAuthenticationFilter bearerSessionAuthenticationFilter,
            PublicRateLimitFilter publicRateLimitFilter,
            CookieCsrfProtectionFilter cookieCsrfProtectionFilter,
            ApiErrorWriter apiErrorWriter,
            @Qualifier("hostveroCorsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/", "/index.html", "/guest/**", "/css/**", "/js/**", "/images/**",
                                "/favicon.ico").permitAll()
                        .requestMatchers("/api/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/public/guest/**").permitAll()
                        .requestMatchers("/api/webhooks/mpesa", "/api/webhooks/stripe", "/api/webhooks/paystack").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                        apiErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_REQUIRED",
                                "Please sign in to continue.", false)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(true)))
                .addFilterBefore(publicRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cookieCsrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(bearerSessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource));
        return http.build();
    }

    @Bean("hostveroCorsConfigurationSource")
    CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.security.cors.allowed-origins}") String allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration(allowedOrigins));
        return source;
    }

    static CorsConfiguration corsConfiguration(String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        return configuration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

}
