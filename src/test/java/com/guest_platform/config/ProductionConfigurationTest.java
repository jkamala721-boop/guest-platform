package com.guest_platform.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationTest {

    @Test
    void acceptsTheConfiguredProductionFoundation() {
        assertThatCode(() -> ProductionConfiguration.validate(validProductionEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void rejectsH2AndNonHttpsPublicUrls() {
        MockEnvironment h2 = validProductionEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:prod");
        assertThatIllegalStateException().isThrownBy(() -> ProductionConfiguration.validate(h2))
                .withMessage("Production requires a PostgreSQL datasource");

        MockEnvironment localhost = validProductionEnvironment()
                .withProperty("app.public-base-url", "http://localhost:8080");
        assertThatIllegalStateException().isThrownBy(() -> ProductionConfiguration.validate(localhost))
                .withMessage("Production public base URL must be a non-localhost HTTPS URL");
    }

    @Test
    void requiresStripeSecretsOnlyWhenStripeLiveModeIsEnabled() {
        MockEnvironment liveStripe = validProductionEnvironment()
                .withProperty("app.payments.stripe.mode", "live");
        assertThatIllegalStateException().isThrownBy(() -> ProductionConfiguration.validate(liveStripe))
                .withMessage("Production configuration is missing app.payments.stripe.secret-key");
    }

    @Test
    void requiresPaystackSecretOnlyWhenPaystackLiveModeIsEnabled() {
        MockEnvironment livePaystack = validProductionEnvironment()
                .withProperty("app.payments.paystack.mode", "live");
        assertThatIllegalStateException().isThrownBy(() -> ProductionConfiguration.validate(livePaystack))
                .withMessage("Production configuration is missing app.payments.paystack.secret-key");
    }

    private MockEnvironment validProductionEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://pooler.example.test:5432/postgres?sslmode=require")
                .withProperty("app.public-base-url", "https://app.example.test")
                .withProperty("app.notifications.default-channel", "EMAIL")
                .withProperty("app.notifications.resend.enabled", "true")
                .withProperty("app.notifications.resend.api-key", "test-only-key")
                .withProperty("app.notifications.resend.from", "noreply@example.test")
                .withProperty("app.security.cors.allowed-origins", "https://app.hostvero.net")
                .withProperty("app.security.payout-fingerprint-secret", "test-only-fingerprint-key")
                .withProperty("app.payments.stripe.mode", "mock")
                .withProperty("app.payments.paystack.mode", "mock")
                .withProperty("app.notifications.whatsapp.enabled", "false")
                .withProperty("app.payments.mpesa.mode", "mock");
    }

    @Test
    void requiresDedicatedPayoutFingerprintSecretAndExactProductionCorsOrigin() {
        MockEnvironment missingFingerprint = validProductionEnvironment()
                .withProperty("app.security.payout-fingerprint-secret", "");
        assertThatIllegalStateException().isThrownBy(() -> ProductionConfiguration.validate(missingFingerprint))
                .withMessage("Production configuration is missing app.security.payout-fingerprint-secret");

        MockEnvironment wildcardCors = validProductionEnvironment()
                .withProperty("app.security.cors.allowed-origins", "*");
        assertThatIllegalStateException().isThrownBy(() -> ProductionConfiguration.validate(wildcardCors))
                .withMessage("Production CORS origin must be https://app.hostvero.net");
    }
}
