package com.vindhyanstudio.config;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of everything under {@code app.*} in application*.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        String frontendUrl,
        String corsOrigins,
        Seller seller,
        Admin admin,
        Auth auth,
        Catalogue catalogue,
        Storage storage,
        Pricing pricing,
        Payment payment,
        Shipping shipping,
        Notifications notifications) {

    public List<String> corsOriginList() {
        return split(corsOrigins);
    }

    public record Seller(String name, String legalName, String address, String state, String gstin, String email, String phone, String whatsapp, String defaultHsn) {}

    public record Admin(String identities) {
        public List<String> identityList() {
            return split(identities).stream().map(String::toLowerCase).toList();
        }
    }

    public record Auth(String jwtSecret, int tokenTtlHours, int otpLength, int otpTtlSeconds,
                       int otpResendSeconds, int otpMaxAttempts, boolean exposeOtp) {}

    public record Catalogue(String source, String localPath, boolean syncOnStartup, String syncCron, boolean publishOnly) {}

    public record Storage(String provider, String localPath, Supabase supabase) {
        public record Supabase(String url, String serviceKey, String publicBucket, String privateBucket) {}
    }

    public record Pricing(BigDecimal shippingFlatFee, BigDecimal freeShippingAbove,
                          boolean codEnabled, BigDecimal codMaxOrderValue) {}

    public record Payment(String provider, boolean refundOnCancel, Razorpay razorpay, Cashfree cashfree) {
        public record Razorpay(String keyId, String keySecret, String webhookSecret) {}
        public record Cashfree(String appId, String secretKey, String environment) {}
    }

    public record Shipping(String provider, Shiprocket shiprocket) {
        public record Shiprocket(String email, String password, String pickupLocation,
                                 String pickupPincode, String webhookToken, String courierId,
                                 boolean autoBook, boolean cancelOnOrderCancel) {}
    }

    public record Notifications(String email, String sms, String fromEmail, String brevoApiKey) {}

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
