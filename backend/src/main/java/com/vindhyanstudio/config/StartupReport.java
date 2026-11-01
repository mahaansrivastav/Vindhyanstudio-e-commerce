/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.config;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.sql.Connection;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootVersion;
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
    private final DataSource dataSource;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss z");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** AWS region code (in Supabase pooler hosts) -> city, so the log says where the database lives. */
    private static final Map<String, String> REGIONS = Map.ofEntries(
            Map.entry("ap-south-1", "Mumbai, India"), Map.entry("ap-southeast-1", "Singapore"),
            Map.entry("ap-southeast-2", "Sydney"), Map.entry("ap-northeast-1", "Tokyo"),
            Map.entry("ap-northeast-2", "Seoul"), Map.entry("eu-central-1", "Frankfurt"),
            Map.entry("eu-central-2", "Zurich"), Map.entry("eu-west-1", "Ireland"),
            Map.entry("eu-west-2", "London"), Map.entry("eu-west-3", "Paris"),
            Map.entry("eu-north-1", "Stockholm"), Map.entry("us-east-1", "N. Virginia, USA"),
            Map.entry("us-east-2", "Ohio, USA"), Map.entry("us-west-1", "N. California, USA"),
            Map.entry("us-west-2", "Oregon, USA"), Map.entry("ca-central-1", "Canada"),
            Map.entry("sa-east-1", "Sao Paulo"));

    public StartupReport(AppProperties p, Environment env, JdbcTemplate jdbc, ObjectProvider<Flyway> flyway, DataSource dataSource) {
        this.p = p;
        this.env = env;
        this.jdbc = jdbc;
        this.flyway = flyway;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report(ApplicationReadyEvent event) {
        try {
            log.info("\n{}", build(event.getTimeTaken()));
        } catch (Exception e) {
            log.warn("Startup report failed: {}", e.toString());
        }
    }

    private String build(Duration startup) {
        List<String> warn = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        String[] profiles = env.getActiveProfiles().length > 0 ? env.getActiveProfiles() : env.getDefaultProfiles();
        boolean prod = Arrays.asList(profiles).contains("prod");
        String stage = prod ? "PRODUCTION" : Arrays.asList(profiles).contains("dev") ? "STAGING / DEV" : "LOCAL";

        line(b, "=".repeat(72));
        line(b, "  VINDHYAN STUDIO BACKEND IS UP   [" + stage + "]");
        line(b, "  Software (c) 2026 Mahan Srivastava. All rights reserved. Licensed, not sold (see LICENSE).");
        line(b, "=".repeat(72));

        section(b, "Build & host");
        row(b, "Started at", ZonedDateTime.now(IST).format(TS) + "  (startup took " + (startup == null ? "?" : startup.toMillis() / 1000.0 + "s") + ")");
        row(b, "Profile", String.join(", ", profiles));
        row(b, "Spring Boot / Java", SpringBootVersion.getVersion() + " / " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        row(b, "Host", hostName() + " | " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        row(b, "CPU / memory", Runtime.getRuntime().availableProcessors() + " cores, max heap " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
        row(b, "Server timezone", TimeZone.getDefault().getID());
        row(b, "PID", ManagementFactory.getRuntimeMXBean().getPid());
        if (set(System.getenv("RENDER"))) {
            row(b, "Render service", System.getenv().getOrDefault("RENDER_SERVICE_NAME", "?") + " (" + System.getenv().getOrDefault("RENDER_INSTANCE_ID", "?") + ")");
            row(b, "Git commit", System.getenv().getOrDefault("RENDER_GIT_BRANCH", "?") + " @ " + abbrev(System.getenv("RENDER_GIT_COMMIT"))
                    + "  " + System.getenv().getOrDefault("RENDER_GIT_REPO_SLUG", ""));
            row(b, "Render URL", System.getenv().getOrDefault("RENDER_EXTERNAL_URL", "?"));
        }

        section(b, "URLs");
        row(b, "Port", env.getProperty("local.server.port", env.getProperty("server.port", "?")));
        row(b, "Backend (public)", p.publicBaseUrl());
        row(b, "Health check", p.publicBaseUrl() + "/actuator/health");
        row(b, "Store config", p.publicBaseUrl() + "/api/config");
        row(b, "Products API", p.publicBaseUrl() + "/api/catalogue/products");
        row(b, "Payment webhook", p.publicBaseUrl() + "/api/webhooks/payments");
        row(b, "Tracking webhook", p.publicBaseUrl() + "/api/webhooks/tracking");
        row(b, "Frontend", p.frontendUrl());
        row(b, "Admin panel", p.frontendUrl() + "/admin");
        row(b, "CORS allowed", String.join(", ", p.corsOriginList()));
        if (!p.corsOriginList().contains(p.frontendUrl())) warn.add("APP_FRONTEND_URL (" + p.frontendUrl() + ") is not in APP_CORS_ORIGINS; the site cannot call the API.");
        if (prod && p.publicBaseUrl().contains("localhost")) warn.add("APP_PUBLIC_BASE_URL is localhost; set it to the Render URL.");
        if (prod && p.corsOriginList().stream().anyMatch(o -> o.contains("localhost")))
            warn.add("APP_CORS_ORIGINS still contains localhost.");

        section(b, "Database");
        String url = env.getProperty("spring.datasource.url", "");
        String dbUser = env.getProperty("spring.datasource.username", "");
        row(b, "Type", url.startsWith("jdbc:h2") ? "H2 (local file)" : url.startsWith("jdbc:postgresql") ? "PostgreSQL" : url);
        row(b, "Host", dbHost(url));
        row(b, "Location", dbLocation(url));
        String ref = supabaseRef(url, dbUser);
        if (ref != null) {
            row(b, "Supabase project", ref);
            row(b, "Supabase dashboard", "https://supabase.com/dashboard/project/" + ref);
        }
        row(b, "Database / user", dbName(url) + " / " + (set(dbUser) ? dbUser : "-"));
        if (url.contains(":6543")) warn.add("DB_URL uses the transaction pooler (6543); use the session pooler (5432).");
        if (url.startsWith("jdbc:postgresql") && !url.contains("sslmode")) warn.add("DB_URL has no ?sslmode=require.");
        row(b, "Pool size (max)", env.getProperty("spring.datasource.hikari.maximum-pool-size", "10 (default)"));
        try (Connection c = dataSource.getConnection()) {
            var md = c.getMetaData();
            row(b, "Server", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion().split(" ")[0]);
        } catch (Exception e) {
            row(b, "Server", "?");
        }
        try {
            long t0 = System.nanoTime();
            jdbc.queryForObject("select 1", Integer.class);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            row(b, "Connection", "OK  (round trip " + ms + " ms)");
            if (ms > 150) warn.add("Database round trip is " + ms + " ms; the DB is far from this server (move them to the same region).");
            Flyway f = flyway.getIfAvailable();
            if (f != null && f.info().current() != null) {
                row(b, "Schema version", "V" + f.info().current().getVersion() + " - " + f.info().current().getDescription()
                        + (f.info().pending().length > 0 ? "  (" + f.info().pending().length + " pending!)" : ""));
            }
            row(b, "Products", count("select count(*) from products where active = true") + " visible / "
                    + count("select count(*) from products") + " total, "
                    + count("select count(*) from product_images") + " photos");
            row(b, "Categories", count("select count(*) from categories"));
            row(b, "Customers", count("select count(*) from customers"));
            row(b, "Orders", count("select count(*) from orders") + " total; " + ordersByStatus());
            row(b, "Last order", one("select order_no || ' at ' || cast(created_at as varchar) from orders order by created_at desc limit 1"));
            row(b, "Last catalogue sync", one("select cast(finished_at as varchar) || ' (' || rows_ok || ' ok, ' || rows_failed || ' failed)' from sync_runs order by started_at desc limit 1"));
            row(b, "Failed notifications", count("select count(*) from notification_log where status = 'FAILED'") + " of " + count("select count(*) from notification_log"));
            if (count("select count(*) from products where active = true").equals("0"))
                warn.add("No visible products. Publish the catalogue (scripts/publish-catalogue.sh) or add in Admin.");
        } catch (Exception e) {
            row(b, "Connection", "FAILED: " + e.getMessage());
            warn.add("Database check failed.");
        }

        section(b, "Login");
        boolean emailReal = "smtp".equals(p.notifications().email()) || "brevo".equals(p.notifications().email());
        boolean phoneLogin = p.auth().exposeOtp() || !"console".equals(p.notifications().sms());
        row(b, "Email OTP", emailReal ? "ON (codes sent by email)" : "ON (codes only printed in this log)");
        row(b, "Mobile OTP", phoneLogin ? "ON" : "OFF (no SMS/WhatsApp sender configured)");
        row(b, "Show OTP on screen", p.auth().exposeOtp() ? "ON (testing only!)" : "OFF");
        row(b, "Session length", p.auth().tokenTtlHours() + " hours");
        row(b, "JWT secret", DEFAULT_JWT.equals(p.auth().jwtSecret()) ? "DEFAULT (unsafe)" : "set");
        row(b, "Admins", String.join(", ", p.admin().identityList().stream().map(StartupReport::maskId).toList()));
        row(b, "Developers", p.admin().developerList().isEmpty() ? "none (set DEVELOPER_IDENTITIES)" : String.join(", ", p.admin().developerList().stream().map(StartupReport::maskId).toList()));
        row(b, "Analytics viewers", p.admin().analystList().isEmpty() ? "none" : String.join(", ", p.admin().analystList().stream().map(StartupReport::maskId).toList()));
        row(b, "Usage tracking", "ON (page views + logins, kept " + p.analytics().retentionDays() + " days)");
        if (prod && p.auth().exposeOtp()) warn.add("OTP is shown on screen in production; anyone can log in as anyone.");
        if (prod && DEFAULT_JWT.equals(p.auth().jwtSecret())) warn.add("JWT_SECRET is the default; set a random value.");
        if (!emailReal && prod) warn.add("NOTIFY_EMAIL is 'console'; customers will not receive login codes.");
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
        row(b, "Email", "brevo".equals(p.notifications().email())
                ? "Brevo API (" + (set(p.notifications().brevoApiKey()) ? "key set" : "BREVO_API_KEY MISSING") + ")"
                : emailReal ? "SMTP via " + env.getProperty("spring.mail.host", "(host MISSING)") + ":" + env.getProperty("spring.mail.port", "?")
                + " as " + (set(env.getProperty("spring.mail.username")) ? maskId(env.getProperty("spring.mail.username")) : "(user MISSING)")
                + (set(env.getProperty("spring.mail.password")) ? "" : ", password MISSING") : "console (printed in log)");
        row(b, "From", p.notifications().fromEmail());
        row(b, "SMS / WhatsApp", "console".equals(p.notifications().sms()) ? "OFF (console)" : p.notifications().sms());
        row(b, "Owner WhatsApp link", set(p.seller().whatsapp()) ? "wa.me/" + p.seller().whatsapp() : "OFF");
        if ("brevo".equals(p.notifications().email()) && !set(p.notifications().brevoApiKey())) warn.add("NOTIFY_EMAIL=brevo but BREVO_API_KEY is not set; emails will fail.");
        if ("smtp".equals(p.notifications().email()) && !set(env.getProperty("spring.mail.host"))) warn.add("NOTIFY_EMAIL=smtp but SPRING_MAIL_HOST is not set.");
        String st = p.storage().provider();
        row(b, "File storage", "supabase".equals(st)
                ? "Supabase (" + (set(p.storage().supabase().url()) ? dbHost(p.storage().supabase().url()) : "URL MISSING") + ")"
                : "Local folder " + p.storage().localPath());
        if ("supabase".equals(st)) {
            row(b, "Buckets", p.storage().supabase().publicBucket() + " (public), " + p.storage().supabase().privateBucket() + " (private)");
            row(b, "Service key", set(p.storage().supabase().serviceKey()) ? "set" : "MISSING");
        }
        if ("supabase".equals(st) && !set(p.storage().supabase().serviceKey())) warn.add("SUPABASE_SERVICE_KEY missing; uploads will fail.");
        if (prod && "local".equals(st)) warn.add("STORAGE_PROVIDER=local in production; uploaded files are lost on redeploy.");
        section(b, "Background jobs");
        row(b, "Payment reconcile", "every " + Long.parseLong(env.getProperty("app.payment.reconcile-interval-ms", "300000")) / 60000 + " min"
                + ("razorpay".equals(p.payment().provider()) || "cashfree".equals(p.payment().provider()) ? "" : " (idle, no gateway)"));
        String cron = p.catalogue().syncCron();
        row(b, "Catalogue re-sync", cron == null || cron.equals("-") ? "OFF" : "cron " + cron);
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

    private String one(String sql) {
        try {
            String v = jdbc.queryForObject(sql, String.class);
            return v == null ? "-" : v;
        } catch (Exception e) {
            return "none";
        }
    }

    private String ordersByStatus() {
        try {
            var rows = jdbc.queryForList("select status, count(*) as n from orders group by status order by status");
            if (rows.isEmpty()) return "none";
            return String.join(", ", rows.stream().map(r -> r.get("status") + " " + r.get("n")).toList());
        } catch (Exception e) {
            return "?";
        }
    }

    private static String dbLocation(String url) {
        if (url.startsWith("jdbc:h2")) return "this machine";
        for (var e : REGIONS.entrySet()) if (url.contains(e.getKey() + ".")) return e.getValue() + " (" + e.getKey() + ")";
        return "unknown (direct host; check Supabase > Settings > General)";
    }

    private static String supabaseRef(String url, String user) {
        if (user != null && user.startsWith("postgres.")) return user.substring("postgres.".length());
        var m = java.util.regex.Pattern.compile("db\\.([a-z0-9]+)\\.supabase\\.co").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static String dbName(String url) {
        if (url.startsWith("jdbc:h2")) return url.replaceAll("^jdbc:h2:(file:)?([^;]+).*", "$2");
        try {
            String path = URI.create(url.replaceFirst("^jdbc:", "")).getPath();
            return path == null || path.isBlank() ? "-" : path.substring(1);
        } catch (Exception e) {
            return url.startsWith("jdbc:h2") ? url.replaceAll("^jdbc:h2:(file:)?([^;]+).*", "$2") : "-";
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "?";
        }
    }

    private static String abbrev(String sha) {
        return sha == null ? "?" : sha.substring(0, Math.min(7, sha.length()));
    }

    private static String dbHost(String url) {
        if (url == null || url.isBlank()) return "-";
        if (url.startsWith("jdbc:h2")) return "this machine";
        try {
            URI u = URI.create(url.replaceFirst("^jdbc:", ""));
            return u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
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
