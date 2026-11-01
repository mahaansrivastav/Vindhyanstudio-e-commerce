/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.analytics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.storage.StorageService;

/**
 * Website usage numbers. {@link #overview} is aggregate-only (safe for analysts);
 * the rest returns personal data (IP, email) and is for the developer role only.
 */
@Service
public class AnalyticsService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String SOLD = "('CONFIRMED','PRINTING','READY','DISPATCHED','DELIVERED')";

    private final JdbcTemplate jdbc;
    private final StorageService storage;

    public AnalyticsService(JdbcTemplate jdbc, StorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public record Kpis(long views, long visitors, long sessions, long signedInVisitors, long logins, long failedLogins,
                       long signups, long orders, BigDecimal revenue, double conversion) {}

    public record Point(String date, String label, long views, long visitors) {}

    public record Count(String name, long count) {}

    public record PageStat(String path, long views, long visitors) {}

    public record ProductStat(String sku, String name, String image, long views, long visitors, long ordered) {}

    public record Overview(String from, String to, Kpis kpis, Kpis previous, List<Point> series, List<PageStat> pages,
                           List<ProductStat> products, List<Count> sources, List<Count> devices, List<Count> browsers,
                           List<Count> os, List<Count> regions, List<Count> languages, long[] hours, List<Count> areas) {}

    public record Login(String at, String identity, String name, String method, boolean success, boolean newAccount,
                        String reason, String ip, String device, String browser, String os) {}

    public record Visit(String at, String path, String area, String source, String ip, String device, String browser,
                        String os, String customer, String visitor, String timezone) {}

    public record Visitor(String visitor, String firstSeen, String lastSeen, long views, long sessions, String ip,
                          String device, String browser, String customer, String source, String timezone) {}

    record Range(LocalDate from, LocalDate to) {
        OffsetDateTime start() { return from.atStartOfDay(IST).toOffsetDateTime(); }
        OffsetDateTime end() { return to.plusDays(1).atStartOfDay(IST).toOffsetDateTime(); }
        long days() { return ChronoUnit.DAYS.between(from, to) + 1; }
        Range previous() { return new Range(from.minusDays(days()), from.minusDays(1)); }
    }

    // ---------- aggregate (analysts + developer) ----------

    public Overview overview(LocalDate from, LocalDate to, boolean includeInternal) {
        Range r = range(from, to);
        String scope = includeInternal ? "" : " and area = 'STORE'";
        Object[] p = {r.start(), r.end()};

        List<Point> series = series(r, scope);
        List<PageStat> pages = jdbc.query("select path, count(*) v, count(distinct visitor_id) u from page_views where at >= ? and at < ?" + scope
                        + " group by path order by v desc limit 15",
                (rs, i) -> new PageStat(rs.getString(1), rs.getLong(2), rs.getLong(3)), p);

        Map<String, Long> ordered = new HashMap<>();
        jdbc.query("select i.sku, sum(i.qty) from order_items i join orders o on o.id = i.order_id where o.created_at >= ? and o.created_at < ? and o.status in "
                + SOLD + " group by i.sku", rs -> { ordered.put(rs.getString(1), rs.getLong(2)); }, p);
        List<ProductStat> products = jdbc.query("""
                        select v.sku, p.name, count(*) views, count(distinct v.visitor_id) visitors,
                               (select pi.storage_key from product_images pi where pi.sku = v.sku order by pi.sort_order limit 1) img
                        from page_views v left join products p on p.sku = v.sku
                        where v.at >= ? and v.at < ? and v.sku is not null
                        group by v.sku, p.name order by views desc limit 15""",
                (rs, i) -> new ProductStat(rs.getString(1), rs.getString(2), rs.getString(5) == null ? null : storage.publicUrl(rs.getString(5)),
                        rs.getLong(3), rs.getLong(4), ordered.getOrDefault(rs.getString(1), 0L)), p);

        return new Overview(r.from().toString(), r.to().toString(), kpis(r, scope), kpis(r.previous(), scope), series, pages, products,
                sources(r, scope),
                counts("device", "count(distinct visitor_id)", r, scope),
                counts("browser", "count(distinct visitor_id)", r, scope),
                counts("os", "count(distinct visitor_id)", r, scope),
                counts("coalesce(timezone, 'unknown')", "count(distinct visitor_id)", r, scope),
                counts("coalesce(language, 'unknown')", "count(distinct visitor_id)", r, scope),
                hours(r, scope),
                counts("area", "count(*)", r, ""));
    }

    private Kpis kpis(Range r, String scope) {
        Object[] p = {r.start(), r.end()};
        Map<String, Object> v = jdbc.queryForMap("select count(*) views, count(distinct visitor_id) visitors, count(distinct session_id) sessions, "
                + "count(distinct customer_id) signed from page_views where at >= ? and at < ?" + scope, p);
        Map<String, Object> l = jdbc.queryForMap("select coalesce(sum(case when success then 1 else 0 end), 0) ok, "
                + "coalesce(sum(case when success then 0 else 1 end), 0) bad from login_events where at >= ? and at < ?", p);
        long signups = num(jdbc.queryForObject("select count(*) from customers where created_at >= ? and created_at < ?", Long.class, p));
        Map<String, Object> o = jdbc.queryForMap("select count(*) n, coalesce(sum(total), 0) t from orders where created_at >= ? and created_at < ? and status in " + SOLD, p);
        long visitors = num(v.get("visitors")), orders = num(o.get("n"));
        return new Kpis(num(v.get("views")), visitors, num(v.get("sessions")), num(v.get("signed")), num(l.get("ok")), num(l.get("bad")),
                signups, orders, new BigDecimal(String.valueOf(o.get("t"))), visitors == 0 ? 0 : Math.min(100, Math.round(orders * 1000.0 / visitors) / 10.0));
    }

    private List<Point> series(Range r, String scope) {
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        Map<LocalDate, Set<String>> uniq = new HashMap<>();
        for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) byDay.put(d, new long[2]);
        jdbc.query("select at, visitor_id from page_views where at >= ? and at < ?" + scope, rs -> {
            LocalDate d = rs.getTimestamp(1).toInstant().atZone(IST).toLocalDate();
            long[] c = byDay.get(d);
            if (c == null) return;
            c[0]++;
            uniq.computeIfAbsent(d, k -> new HashSet<>()).add(rs.getString(2));
        }, r.start(), r.end());
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM");
        return byDay.entrySet().stream().map(e -> new Point(e.getKey().toString(), e.getKey().format(f), e.getValue()[0],
                uniq.getOrDefault(e.getKey(), Set.of()).size())).toList();
    }

    private long[] hours(Range r, String scope) {
        long[] h = new long[24];
        jdbc.query("select at from page_views where at >= ? and at < ?" + scope,
                rs -> { h[rs.getTimestamp(1).toInstant().atZone(IST).getHour()]++; }, r.start(), r.end());
        return h;
    }

    private List<Count> counts(String col, String measure, Range r, String scope) {
        return jdbc.query("select " + col + " k, " + measure + " n from page_views where at >= ? and at < ?" + scope
                        + " group by " + col + " order by n desc limit 12",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), r.start(), r.end());
    }

    /** Sessions by how they started: campaign tag or referring site; sessions without one count as "direct". */
    private List<Count> sources(Range r, String scope) {
        return jdbc.query("select coalesce(s.src, 'direct') k, count(*) n from (select session_id, max(source) src from page_views where at >= ? and at < ?"
                        + scope + " group by session_id) s group by coalesce(s.src, 'direct') order by n desc limit 12",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), r.start(), r.end());
    }

    // ---------- personal data (developer only) ----------

    public List<Login> logins(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        return jdbc.query("""
                        select l.at, l.identity, c.name, l.method, l.success, l.new_account, l.reason, l.ip, l.device, l.browser, l.os
                        from login_events l left join customers c on c.id = l.customer_id
                        where l.at >= ? and l.at < ? order by l.at desc limit 1000""",
                (rs, i) -> new Login(ts(rs.getTimestamp(1)), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        rs.getBoolean(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)),
                r.start(), r.end());
    }

    public List<Visit> visits(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        return jdbc.query("""
                        select v.at, v.path, v.area, v.source, v.ip, v.device, v.browser, v.os, coalesce(c.email, c.phone), v.visitor_id, v.timezone
                        from page_views v left join customers c on c.id = v.customer_id
                        where v.at >= ? and v.at < ? order by v.at desc limit 1000""",
                (rs, i) -> new Visit(ts(rs.getTimestamp(1)), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)),
                r.start(), r.end());
    }

    public List<Visitor> visitors(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        return jdbc.query("""
                        select v.visitor_id, min(v.at), max(v.at), count(*), count(distinct v.session_id), max(v.ip), max(v.device), max(v.browser),
                               max(coalesce(c.email, c.phone)), max(v.source), max(v.timezone)
                        from page_views v left join customers c on c.id = v.customer_id
                        where v.at >= ? and v.at < ? group by v.visitor_id order by max(v.at) desc limit 1000""",
                (rs, i) -> new Visitor(rs.getString(1), ts(rs.getTimestamp(2)), ts(rs.getTimestamp(3)), rs.getLong(4), rs.getLong(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)),
                r.start(), r.end());
    }

    public byte[] export(LocalDate from, LocalDate to) throws IOException {
        Overview o = overview(from, to, true);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);

            Sheet s = wb.createSheet("Summary");
            Object[][] rows = {
                    {"Period", o.from() + " to " + o.to()}, {"Page views", o.kpis().views()}, {"Visitors", o.kpis().visitors()},
                    {"Sessions", o.kpis().sessions()}, {"Signed-in visitors", o.kpis().signedInVisitors()}, {"Logins", o.kpis().logins()},
                    {"Failed logins", o.kpis().failedLogins()}, {"New sign-ups", o.kpis().signups()}, {"Orders", o.kpis().orders()},
                    {"Revenue (₹)", o.kpis().revenue()}, {"Visitor → order conversion %", o.kpis().conversion()}};
            for (int i = 0; i < rows.length; i++) row(s.createRow(i), rows[i]);
            s.setColumnWidth(0, 32 * 256);
            s.setColumnWidth(1, 24 * 256);

            sheet(wb, head, "Daily", new String[] {"Date", "Page views", "Visitors"},
                    o.series().stream().map(x -> new Object[] {x.date(), x.views(), x.visitors()}).toList());
            sheet(wb, head, "Products viewed", new String[] {"SKU", "Product", "Views", "Visitors", "Ordered (qty)"},
                    o.products().stream().map(x -> new Object[] {x.sku(), x.name(), x.views(), x.visitors(), x.ordered()}).toList());
            sheet(wb, head, "Pages", new String[] {"Path", "Views", "Visitors"},
                    o.pages().stream().map(x -> new Object[] {x.path(), x.views(), x.visitors()}).toList());
            sheet(wb, head, "Sources", new String[] {"Source", "Sessions"}, o.sources().stream().map(x -> new Object[] {x.name(), x.count()}).toList());
            sheet(wb, head, "Logins", new String[] {"Time (IST)", "Email / mobile", "Name", "Method", "Success", "New account", "Reason", "IP", "Device", "Browser", "OS"},
                    logins(from, to).stream().map(x -> new Object[] {x.at(), x.identity(), x.name(), x.method(), x.success() ? "yes" : "no",
                            x.newAccount() ? "yes" : "", x.reason(), x.ip(), x.device(), x.browser(), x.os()}).toList());
            sheet(wb, head, "Visitors", new String[] {"Visitor", "First seen", "Last seen", "Views", "Sessions", "IP", "Device", "Browser", "Customer", "Source", "Time zone"},
                    visitors(from, to).stream().map(x -> new Object[] {x.visitor(), x.firstSeen(), x.lastSeen(), x.views(), x.sessions(), x.ip(),
                            x.device(), x.browser(), x.customer(), x.source(), x.timezone()}).toList());
            sheet(wb, head, "Page views (latest 1000)", new String[] {"Time (IST)", "Path", "Area", "Source", "IP", "Device", "Browser", "OS", "Customer", "Visitor"},
                    visits(from, to).stream().map(x -> new Object[] {x.at(), x.path(), x.area(), x.source(), x.ip(), x.device(), x.browser(),
                            x.os(), x.customer(), x.visitor()}).toList());
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---------- helpers ----------

    private static void sheet(Workbook wb, CellStyle head, String name, String[] cols, List<Object[]> data) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(head);
            s.setColumnWidth(i, Math.max(12, Math.min(40, cols[i].length() + 6)) * 256);
        }
        for (int r = 0; r < data.size(); r++) row(s.createRow(r + 1), data.get(r));
        s.createFreezePane(0, 1);
    }

    private static void row(Row row, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            Cell c = row.createCell(i);
            if (v instanceof Number n) c.setCellValue(n.doubleValue());
            else c.setCellValue(v == null ? "" : String.valueOf(v));
        }
    }

    private static Range range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(IST);
        LocalDate f = from == null ? today.minusDays(29) : from;
        LocalDate t = to == null ? today : to;
        if (t.isBefore(f)) throw ApiException.badRequest("The end date is before the start date");
        if (ChronoUnit.DAYS.between(f, t) > 366) throw ApiException.badRequest("Choose a period of one year or less");
        return new Range(f, t);
    }

    private static String ts(Timestamp t) {
        return t == null ? null : t.toInstant().atZone(IST).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
