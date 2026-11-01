/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.analytics;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.vindhyanstudio.config.AppProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Records page views (sent by the website) and login attempts, and deletes old rows.
 * Never throws: tracking must not break shopping or login.
 */
@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);
    private static final Pattern PRODUCT = Pattern.compile("^/p/([^/?#]+)");
    private static final Pattern BOT = Pattern.compile("(?i)bot|crawl|spider|slurp|headless|lighthouse|preview|facebookexternalhit|whatsapp/");

    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final Map<String, String> skuBySlug = new ConcurrentHashMap<>();

    public TrackingService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public record PageView(String visitor, String session, String path, String referrer, String src,
                           String tz, String lang, Integer w) {}

    public void pageView(PageView v, Long customerId, HttpServletRequest req) {
        try {
            String ua = req.getHeader("User-Agent");
            if (ua == null || BOT.matcher(ua).find()) return;
            if (v.visitor() == null || v.session() == null || v.path() == null) return;
            String path = cut(v.path(), 300);
            UserAgents.Info info = UserAgents.parse(ua);
            jdbc.update("""
                    insert into page_views (at, visitor_id, session_id, customer_id, path, area, sku, referrer, source, ip,
                                            user_agent, device, browser, os, timezone, language, screen_w)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    OffsetDateTime.now(), cut(v.visitor(), 40), cut(v.session(), 40), customerId, path, area(path), sku(path),
                    cut(v.referrer(), 300), source(v.src(), v.referrer()), clientIp(req), cut(ua, 400),
                    info.device(), info.browser(), info.os(), cut(v.tz(), 60), cut(v.lang(), 20), v.w());
        } catch (Exception e) {
            log.debug("page view not recorded: {}", e.toString());
        }
    }

    /** Own transaction, so failed attempts are kept even though the login request itself fails. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void login(String identity, String method, boolean success, boolean newAccount, Long customerId, String reason, HttpServletRequest req) {
        try {
            String ua = req.getHeader("User-Agent");
            UserAgents.Info info = UserAgents.parse(ua);
            jdbc.update("""
                    insert into login_events (at, customer_id, identity, method, success, new_account, reason, ip, user_agent, device, browser, os)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    OffsetDateTime.now(), customerId, cut(identity, 200), method, success, newAccount, cut(reason, 200),
                    clientIp(req), cut(ua, 400), info.device(), info.browser(), info.os());
        } catch (Exception e) {
            log.debug("login event not recorded: {}", e.toString());
        }
    }

    /** Daily at 03:30: drop raw analytics older than the retention period. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    public void purge() {
        int days = Math.max(30, props.analytics().retentionDays());
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
        int a = jdbc.update("delete from page_views where at < ?", cutoff);
        int b = jdbc.update("delete from login_events where at < ?", cutoff);
        if (a + b > 0) log.info("Analytics retention: removed {} page views and {} login events older than {} days", a, b, days);
    }

    /** Client address behind Render / Netlify proxies (first entry of X-Forwarded-For). */
    static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return cut(fwd.split(",")[0].trim(), 64);
        return cut(req.getRemoteAddr(), 64);
    }

    static String area(String path) {
        if (path.startsWith("/admin")) return "ADMIN";
        if (path.startsWith("/dev")) return "DEV";
        return "STORE";
    }

    private String sku(String path) {
        Matcher m = PRODUCT.matcher(path);
        if (!m.find()) return null;
        String slug = m.group(1);
        String sku = skuBySlug.computeIfAbsent(slug, s -> jdbc.query("select sku from products where slug = ?",
                rs -> rs.next() ? rs.getString(1) : "", s));
        return sku.isEmpty() ? null : sku;
    }

    /** Where the visit came from: a campaign tag (?utm_source=) or the referring site. Null = inside the site. */
    static String source(String utm, String referrer) {
        if (utm != null && !utm.isBlank()) return cut(utm.trim().toLowerCase(Locale.ROOT), 60);
        if (referrer == null || referrer.isBlank()) return null;
        String host;
        try {
            host = URI.create(referrer.trim()).getHost();
        } catch (Exception e) {
            return "other";
        }
        if (host == null) return "other";
        host = host.toLowerCase(Locale.ROOT).replaceFirst("^(www\\.|m\\.|l\\.|lm\\.)", "");
        if (host.contains("vindhyanstudio") || host.contains("netlify.app") || host.equals("localhost")) return null;
        if (host.startsWith("google.") || host.contains(".google.")) return "google";
        if (host.contains("instagram")) return "instagram";
        if (host.contains("facebook") || host.equals("fb.com")) return "facebook";
        if (host.contains("whatsapp") || host.equals("wa.me")) return "whatsapp";
        if (host.contains("youtube") || host.equals("youtu.be")) return "youtube";
        if (host.equals("t.co") || host.contains("twitter") || host.equals("x.com")) return "x";
        if (host.contains("bing.")) return "bing";
        if (host.contains("linkedin") || host.equals("lnkd.in")) return "linkedin";
        if (host.contains("pinterest")) return "pinterest";
        return cut(host, 60);
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
