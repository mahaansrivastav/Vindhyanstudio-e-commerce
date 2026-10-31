package com.vindhyanstudio.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prints one block to the log once the backend is ready: environment, URLs, database,
 * which features/integrations are on, and warnings for anything missing or unsafe.
 * Never prints secret values, only whether they are set.
 */
@Component
public class StartupReport {

    private static final Logger log = LoggerFactory.getLogger(StartupReport.class);
    private static final String DEFAULT_JWT = "change-me-local-only-secret-change-me-local-only";

    private final AppProperties p;
    private final Environment env;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<Flyway> flyway;

    public StartupReport(AppProperties p, Environment env, JdbcTemplate jdbc, ObjectProvider<Flyway> flyway) {
        this.p = p;
        this.env = env;
        this.jdbc = jdbc;
        this.flyway = flyway;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        try {
            log.info("\n{}", build());
        } catch (Exception e) {
            log.warn("Startup report failed: {}", e.toString());
        }
    }

    private String build() {
        List<String> warn = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        String[] profiles = env.getActiveProfiles().length > 0 ? env.getActiveProfiles() : env.getDefaultProfiles();
        boolean prod = Arrays.asList(profiles).contains("prod");
        String stage = prod ? "PRODUCTION" : Arrays.asList(profiles).contains("dev") ? "STAGING / DEV" : "LOCAL";

        line(b, "=".repeat(72));
        line(b, "  VINDHYAN STUDIO BACKEND IS UP   [" + stage + "]");
        line(b, "=".repeat(72));

        section(b, "Environment");
        row(b, "Profile", String.join(", ", profiles));
        row(b, "Java", System.getProperty("java.version"));
        row(b, "Port", env.getProperty("local.server.port", env.getProperty("server.port", "?")));
        row(b, "Backend URL", p.publicBaseUrl());
        row(b, "Health check", p.publicBaseUrl() + "/actuator/health");
        row(b, "Frontend URL", p.frontendUrl());
        row(b, "CORS allowed", String.join(", ", p.corsOriginList()));
        if (prod && p.publicBaseUrl().contains("localhost")) warn.add("APP_PUBLIC_BASE_URL is localhost; set it to the Render URL.");
        if (prod && p.corsOriginList().stream().anyMatch(o -> o.contains("localhost")))
            warn.add("APP_CORS_ORIGINS still contains localhost.");

        section(b, "Database");
        String url = env.getProperty("spring.datasource.url", "");
        row(b, "Type", url.startsWith("jdbc:h2") ? "H2 (local file)" : url.startsWith("jdbc:postgresql") ? "PostgreSQL" : url);
        row(b, "Host", dbHost(url));
        try {
            jdbc.queryForObject("select 1", Integer.class);
            row(b, "Connection", "OK");
            Flyway f = flyway.getIfAvailable();
            if (f != null && f.info().current() != null) row(b, "Schema version", "V" + f.info().current().getVersion());
            row(b, "Products", count("select count(*) from products where active = true") + " visible / "
                    + count("select count(*) from products") + " total");
            row(b, "Categories", count("select count(*) from categories"));
            row(b, "Customers", count("select count(*) from customers"));
            row(b, "Orders", count("select count(*) from orders"));
            if (count("select count(*) from products where active = true").equals("0"))
                warn.add("No visible products. Publish the catalogue (scripts/publish-catalogue.sh) or add in Admin.");
        } catch (Exception e) {
            row(b, "Connection", "FAILED: " + e.getMessage());
            warn.add("Database check failed.");
        }

        section(b, "Login");
        boolean emailReal = "smtp".equals(p.notifications().email());
        boolean phoneLogin = p.auth().exposeOtp() || !"console".equals(p.notifications().sms());
        row(b, "Email OTP", emailReal ? "ON (codes sent by email)" : "ON (codes only printed in this log)");
        row(b, "Mobile OTP", phoneLogin ? "ON" : "OFF (no SMS/WhatsApp sender configured)");
        row(b, "Show OTP on screen", p.auth().exposeOtp() ? "ON (testing only!)" : "OFF");
        row(b, "Session length", p.auth().tokenTtlHours() + " hours");
        row(b, "JWT secret", DEFAULT_JWT.equals(p.auth().jwtSecret()) ? "DEFAULT (unsafe)" : "set");
        row(b, "Admins", String.join(", ", p.admin().identityList().stream().map(StartupReport::maskId).toList()));
        if (prod && p.auth().exposeOtp()) warn.add("OTP is shown on screen in production; anyone can log in as anyone.");
        if (prod && DEFAULT_JWT.equals(p.auth().jwtSecret())) warn.add("JWT_SECRET is the default; set a random value.");
        if (!emailReal && prod) warn.add("Email is not 'smtp'; customers will not receive login codes.");
        if (p.admin().identityList().stream().anyMatch(a -> a.endsWith(".local")))
            warn.add("ADMIN_IDENTITIES still has the placeholder admin.");

        section(b, "Payments");
        String provider = p.payment().provider();
        row(b, "Online payment", switch (provider) {
            case "razorpay" -> "ON  - Razorpay " + razorpayMode();
            case "cashfree" -> "ON  - Cashfree (" + p.payment().cashfree().environment() + ")";
            case "mock" -> "MOCK (fake payments, local only!)";
            case "disabled" -> "OFF (cash on delivery only)";
            default -> provider;
        });
        if ("razorpay".equals(provider)) {
            var r = p.payment().razorpay();
            row(b, "Razorpay key id", set(r.keyId()) ? r.keyId().substring(0, Math.min(9, r.keyId().length())) + "..." : "MISSING");
            row(b, "Razorpay secret", set(r.keySecret()) ? "set" : "MISSING");
            row(b, "Webhook secret", set(r.webhookSecret()) ? "set" : "MISSING");
            row(b, "Webhook URL", p.publicBaseUrl() + "/api/webhooks/payments");
            if (!set(r.keyId()) || !set(r.keySecret())) warn.add("Razorpay keys missing; checkout will fail.");
            if (!set(r.webhookSecret())) warn.add("RAZORPAY_WEBHOOK_SECRET missing; webhooks ignored (5-min reconciliation still works).");
            if (prod && set(r.keyId()) && r.keyId().startsWith("rzp_test_")) warn.add("Razorpay is in TEST mode; no real money is collected.");
        }
        if ("cashfree".equals(provider)) {
            var c = p.payment().cashfree();
            row(b, "Cashfree app id", set(c.appId()) ? "set" : "MISSING");
            row(b, "Cashfree secret", set(c.secretKey()) ? "set" : "MISSING");
            if (!set(c.appId()) || !set(c.secretKey())) warn.add("Cashfree keys missing; checkout will fail.");
        }
        if (prod && "mock".equals(provider)) warn.add("PAYMENT_PROVIDER=mock in production; anyone can 'pay' without paying!");
        row(b, "Refund on cancel", onOff(p.payment().refundOnCancel()));
        row(b, "Cash on delivery", p.pricing().codEnabled() ? "ON (up to Rs." + p.pricing().codMaxOrderValue() + ")" : "OFF");
        row(b, "Shipping fee", "Rs." + p.pricing().shippingFlatFee() + ", free above Rs." + p.pricing().freeShippingAbove());
        if ("disabled".equals(provider) && !p.pricing().codEnabled()) warn.add("Online payment AND COD are off; nobody can order.");

        section(b, "Shipping");
        var s = p.shipping();
        row(b, "Provider", switch (s.provider()) {
            case "shiprocket" -> "Shiprocket (automatic)";
            case "manual" -> "Manual (owner books courier)";
            case "mock" -> "MOCK (fake AWB, local only)";
            default -> s.provider();
        });
        if ("shiprocket".equals(s.provider())) {
            var sr = s.shiprocket();
            row(b, "API login", set(sr.email()) && set(sr.password()) ? "set" : "MISSING");
            row(b, "Pickup", sr.pickupLocation() + " / " + (set(sr.pickupPincode()) ? sr.pickupPincode() : "pincode MISSING"));
            row(b, "Webhook token", set(sr.webhookToken()) ? "set" : "MISSING");
            row(b, "Auto-book courier", sr.autoBook() ? "ON (after payment)" : "OFF (when marked Packed)");
            row(b, "Courier", set(sr.courierId()) ? "forced id " + sr.courierId() : "Shiprocket recommended");
            if (!set(sr.email()) || !set(sr.password())) warn.add("Shiprocket login missing; shipments will fail.");
        }
        if (prod && "mock".equals(s.provider())) warn.add("SHIPPING_PROVIDER=mock in production.");

        section(b, "Notifications & storage");
        row(b, "Email", emailReal ? "SMTP via " + env.getProperty("spring.mail.host", "(host MISSING)") : "console (printed in log)");
        row(b, "From", p.notifications().fromEmail());
        row(b, "SMS / WhatsApp", "console".equals(p.notifications().sms()) ? "OFF (console)" : p.notifications().sms());
        row(b, "Owner WhatsApp link", set(p.seller().whatsapp()) ? "wa.me/" + p.seller().whatsapp() : "OFF");
        if (emailReal && !set(env.getProperty("spring.mail.host"))) warn.add("NOTIFY_EMAIL=smtp but SPRING_MAIL_HOST is not set.");
        String st = p.storage().provider();
        row(b, "File storage", "supabase".equals(st)
                ? "Supabase (" + (set(p.storage().supabase().url()) ? dbHost(p.storage().supabase().url()) : "URL MISSING") + ")"
                : "Local folder " + p.storage().localPath());
        if ("supabase".equals(st) && !set(p.storage().supabase().serviceKey())) warn.add("SUPABASE_SERVICE_KEY missing; uploads will fail.");
        if (prod && "local".equals(st)) warn.add("STORAGE_PROVIDER=local in production; uploaded files are lost on redeploy.");
        row(b, "Catalogue sync", p.catalogue().syncOnStartup() ? "ON (from " + p.catalogue().localPath() + ")" : "OFF (publish from laptop / Admin)");

        section(b, "Business (invoice & website)");
        var sel = p.seller();
        row(b, "Brand", sel.name());
        row(b, "Legal name", set(sel.legalName()) ? sel.legalName() : "(not set, brand used)");
        row(b, "GSTIN", set(sel.gstin()) ? sel.gstin() : "not set (invoices without GST no.)");
        row(b, "State", sel.state());
        row(b, "Default HSN", set(sel.defaultHsn()) ? sel.defaultHsn() : "not set");
        row(b, "Email / phone", sel.email() + " / " + sel.phone());
        if (sel.address() != null && sel.address().startsWith("Address line")) warn.add("SELLER_ADDRESS is the placeholder.");
        if (sel.email() != null && sel.email().endsWith(".local")) warn.add("SELLER_EMAIL is the placeholder.");

        section(b, warn.isEmpty() ? "Checks: all good" : "WARNINGS (" + warn.size() + ")");
        warn.forEach(w -> line(b, "  ! " + w));
        line(b, "=".repeat(72));
        return b.toString();
    }

    private String razorpayMode() {
        String k = p.payment().razorpay().keyId();
        if (!set(k)) return "(keys missing)";
        return k.startsWith("rzp_live_") ? "(LIVE - real money)" : "(TEST mode)";
    }

    private String count(String sql) {
        try {
            return String.valueOf(jdbc.queryForObject(sql, Long.class));
        } catch (Exception e) {
            return "?";
        }
    }

    private static String dbHost(String url) {
        if (url == null || url.isBlank()) return "-";
        if (url.startsWith("jdbc:h2")) return "this machine";
        try {
            URI u = URI.create(url.replaceFirst("^jdbc:", ""));
            String region = u.getHost() != null && u.getHost().contains("pooler.supabase.com")
                    ? " (" + u.getHost().split("\\.")[0] + ")" : "";
            return u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + region;
        } catch (Exception e) {
            return "?";
        }
    }

    /** sr***@gmail.com / 98****54 */
    private static String maskId(String id) {
        int at = id.indexOf('@');
        if (at > 0) return id.substring(0, Math.min(2, at)) + "***" + id.substring(at);
        return id.length() > 4 ? id.substring(0, 2) + "****" + id.substring(id.length() - 2) : "****";
    }

    private static boolean set(String v) { return v != null && !v.isBlank(); }
    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }
    private static void section(StringBuilder b, String t) { line(b, ""); line(b, "-- " + t + " " + "-".repeat(Math.max(0, 66 - t.length()))); }
    private static void row(StringBuilder b, String k, Object v) { line(b, String.format("  %-20s %s", k, v)); }
    private static void line(StringBuilder b, String s) { b.append(s).append('\n'); }
}
