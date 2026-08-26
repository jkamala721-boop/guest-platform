package com.guest_platform.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Validates deployment-only configuration without ever logging configuration values. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration {

    private final Environment environment;

    public ProductionConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        validate(environment);
    }

    static void validate(Environment environment) {
        String datasourceUrl = required(environment, "spring.datasource.url");
        if (!datasourceUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("Production requires a PostgreSQL datasource");
        }

        validatePublicBaseUrl(required(environment, "app.public-base-url"));
        validateAdminPublicBaseUrl(required(environment, "app.admin.public-base-url"));
        if (!"https://app.hostvero.net".equals(required(environment, "app.security.cors.allowed-origins"))) {
            throw new IllegalStateException("Production CORS origin must be https://app.hostvero.net");
        }
        required(environment, "app.security.payout-fingerprint-secret");
        required(environment, "app.security.guest-identity-fingerprint-secret");
        required(environment, "app.security.host-identity-fingerprint-secret");
        required(environment, "app.security.property-access-encryption-key");

        String defaultChannel = required(environment, "app.notifications.default-channel");
        if (!"EMAIL".equals(defaultChannel.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("Production scheduled notifications must use EMAIL");
        }
        if (!enabled(environment, "app.notifications.resend.enabled")) {
            throw new IllegalStateException("Production email notifications must be enabled");
        }
        required(environment, "app.notifications.resend.api-key");
        required(environment, "app.notifications.resend.from");
        required(environment, "app.notifications.resend.host-template-id");

        if ("live".equalsIgnoreCase(value(environment, "app.payments.stripe.mode"))) {
            required(environment, "app.payments.stripe.secret-key");
            required(environment, "app.payments.stripe.webhook-secret");
        }

        if ("live".equalsIgnoreCase(value(environment, "app.payments.paystack.mode"))) {
            required(environment, "app.payments.paystack.secret-key");
        }

        if (enabled(environment, "app.notifications.whatsapp.enabled")) {
            required(environment, "app.notifications.whatsapp.access-token");
            required(environment, "app.notifications.whatsapp.phone-number-id");
            required(environment, "app.notifications.whatsapp.api-version");
            required(environment, "app.notifications.whatsapp.manual-template-name");
            required(environment, "app.notifications.whatsapp.guest-link-template-name");
            required(environment, "app.notifications.whatsapp.scheduled-template-name");
        }

        if (!"mock".equalsIgnoreCase(value(environment, "app.payments.mpesa.mode"))) {
            throw new IllegalStateException("M-Pesa live mode is not available in this deployment");
        }
    }

    private static void validatePublicBaseUrl(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()
                    || "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                throw new IllegalStateException("Production public base URL must be a non-localhost HTTPS URL");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Production public base URL must be a valid HTTPS URL", exception);
        }
    }

    private static void validateAdminPublicBaseUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"admin.hostvero.net".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalStateException("Production admin public base URL must be https://admin.hostvero.net");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Production admin public base URL must be a valid HTTPS URL", exception);
        }
    }

    private static boolean enabled(Environment environment, String key) {
        return Boolean.parseBoolean(value(environment, key));
    }

    private static String required(Environment environment, String key) {
        String value = value(environment, key);
        if (value.isBlank()) {
            throw new IllegalStateException("Production configuration is missing " + key);
        }
        return value;
    }

    private static String value(Environment environment, String key) {
        String value = environment.getProperty(key);
        return value == null ? "" : value.trim();
    }
}
