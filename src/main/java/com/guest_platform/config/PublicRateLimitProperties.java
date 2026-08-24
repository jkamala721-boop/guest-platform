package com.guest_platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for small, in-process limits on unauthenticated attack surfaces. */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class PublicRateLimitProperties {

    private boolean enabled = true;
    private int maxBuckets = 10_000;
    private Limit login = new Limit(10, 900);
    private Limit registration = new Limit(5, 3600);
    private Limit guestLink = new Limit(120, 300);
    private Limit otpRequest = new Limit(5, 900);
    private Limit otpVerify = new Limit(10, 900);
    private Limit paymentInitialization = new Limit(10, 900);
    private Limit paystackWebhook = new Limit(240, 60);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxBuckets() { return maxBuckets; }
    public void setMaxBuckets(int maxBuckets) { this.maxBuckets = maxBuckets; }
    public Limit getLogin() { return login; }
    public void setLogin(Limit login) { this.login = login; }
    public Limit getRegistration() { return registration; }
    public void setRegistration(Limit registration) { this.registration = registration; }
    public Limit getGuestLink() { return guestLink; }
    public void setGuestLink(Limit guestLink) { this.guestLink = guestLink; }
    public Limit getOtpRequest() { return otpRequest; }
    public void setOtpRequest(Limit otpRequest) { this.otpRequest = otpRequest; }
    public Limit getOtpVerify() { return otpVerify; }
    public void setOtpVerify(Limit otpVerify) { this.otpVerify = otpVerify; }
    public Limit getPaymentInitialization() { return paymentInitialization; }
    public void setPaymentInitialization(Limit paymentInitialization) { this.paymentInitialization = paymentInitialization; }
    public Limit getPaystackWebhook() { return paystackWebhook; }
    public void setPaystackWebhook(Limit paystackWebhook) { this.paystackWebhook = paystackWebhook; }

    public static class Limit {
        private int maxRequests;
        private long windowSeconds;

        public Limit() { }

        public Limit(int maxRequests, long windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }

        public int getMaxRequests() { return maxRequests; }
        public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
    }
}
