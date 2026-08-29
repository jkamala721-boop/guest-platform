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
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.guest_platform.security.BearerSessionAuthenticationFilter;
import com.guest_platform.security.ApiErrorWriter;
import com.guest_platform.security.CookieCsrfProtectionFilter;
import com.guest_platform.security.PublicRateLimitFilter;
import com.guest_platform.service.HostSessionService;
import com.guest_platform.service.AdminSessionService;
import com.guest_platform.security.AdminSessionAuthenticationFilter;
import com.guest_platform.security.AdminOriginProtectionFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(PublicRateLimitProperties.class)
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; form-action 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
            + "connect-src 'self'; frame-src https://checkout.paystack.com https://checkout.stripe.com";
    static final String MARKETING_CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; form-action 'self'; script-src 'self' https://www.googletagmanager.com "
            + "'sha256-DMbUiHFoJGhQLvd5TvxNVGbE8YkI7a61I4XVOl9zW+k='; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: https://www.google-analytics.com "
            + "https://www.googletagmanager.com; font-src 'self' data:; connect-src 'self' "
            + "https://www.google-analytics.com https://region1.google-analytics.com "
            + "https://www.googletagmanager.com; frame-src https://www.googletagmanager.com";
    static final String PERMISSIONS_POLICY = "camera=(), geolocation=(), microphone=(), payment=(), usb=()";

    @Bean
    BearerSessionAuthenticationFilter bearerSessionAuthenticationFilter(HostSessionService hostSessionService,
            ApiErrorWriter apiErrorWriter) {
        return new BearerSessionAuthenticationFilter(hostSessionService, apiErrorWriter);
    }

    @Bean
    AdminSessionAuthenticationFilter adminSessionAuthenticationFilter(AdminSessionService adminSessionService,
            ApiErrorWriter apiErrorWriter) {
        return new AdminSessionAuthenticationFilter(adminSessionService, apiErrorWriter);
    }

    @Bean
    AdminOriginProtectionFilter adminOriginProtectionFilter(AdminSessionService adminSessionService,
            @org.springframework.beans.factory.annotation.Value("${app.admin.public-base-url:http://localhost:8080}")
            String adminPublicBaseUrl, ApiErrorWriter apiErrorWriter) {
        return new AdminOriginProtectionFilter(adminSessionService, adminPublicBaseUrl, apiErrorWriter);
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
            AdminSessionAuthenticationFilter adminSessionAuthenticationFilter,
            AdminOriginProtectionFilter adminOriginProtectionFilter,
            PublicRateLimitFilter publicRateLimitFilter,
            ApiErrorWriter apiErrorWriter,
            @Qualifier("adminCorsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.securityMatcher("/api/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/admin/auth/login", "/api/admin/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> apiErrorWriter.write(response,
                                HttpServletResponse.SC_UNAUTHORIZED, "ADMIN_AUTH_REQUIRED",
                                "Admin authentication is required.", false))
                        .accessDeniedHandler((request, response, exception) -> apiErrorWriter.write(response,
                                HttpServletResponse.SC_FORBIDDEN, "ADMIN_FORBIDDEN",
                                "You do not have permission to perform this admin action.", false)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(true)))
                .addFilterBefore(publicRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminOriginProtectionFilter, CorsFilter.class)
                .addFilterBefore(adminSessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            BearerSessionAuthenticationFilter bearerSessionAuthenticationFilter,
            PublicRateLimitFilter publicRateLimitFilter,
            CookieCsrfProtectionFilter cookieCsrfProtectionFilter,
            ApiErrorWriter apiErrorWriter,
            @org.springframework.beans.factory.annotation.Value("${app.site.public-hosts:hostvero.net,www.hostvero.net}")
            String publicHostNames,
            @Qualifier("hostveroCorsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        java.util.Set<String> publicHosts = java.util.Arrays.stream(publicHostNames.split(","))
                .map(String::trim).map(String::toLowerCase).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/", "/index.html", "/guest/**", "/admin/**", "/css/**", "/js/**", "/images/**",
                                "/favicon.ico", "/manifest.webmanifest", "/service-worker.js", "/offline.html").permitAll()
                        .requestMatchers("/for-hosts", "/for-guests", "/pricing", "/safety", "/contact",
                                "/privacy", "/terms", "/host-agreement", "/robots.txt", "/sitemap.xml",
                                "/site/**").permitAll()
                        .requestMatchers("/api/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/public/guest/**").permitAll()
                        .requestMatchers("/api/webhooks/mpesa", "/api/webhooks/stripe", "/api/webhooks/paystack").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                        apiErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_REQUIRED",
                                "Please sign in to continue.", false)))
                .headers(headers -> headers
                        .addHeaderWriter((request, response) -> response.setHeader("Content-Security-Policy",
                                isPublicMarketingRequest(request, publicHosts)
                                        ? MARKETING_CONTENT_SECURITY_POLICY : CONTENT_SECURITY_POLICY))
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

    private static boolean isPublicMarketingRequest(jakarta.servlet.http.HttpServletRequest request,
            java.util.Set<String> publicHosts) {
        if (!publicHosts.contains(request.getServerName().toLowerCase())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/".equals(path) || path.startsWith("/site/")
                || java.util.Set.of("/for-hosts", "/for-guests", "/pricing", "/safety", "/contact",
                        "/privacy", "/terms", "/host-agreement", "/robots.txt", "/sitemap.xml")
                        .contains(path);
    }

    @Bean("hostveroCorsConfigurationSource")
    CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.security.cors.allowed-origins}") String allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration(allowedOrigins));
        return source;
    }

    @Bean("adminCorsConfigurationSource")
    CorsConfigurationSource adminCorsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.admin.public-base-url:http://localhost:8080}")
            String adminOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of(adminOrigin));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", configuration);
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
