/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.admin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vindhyanstudio.catalogue.Product;
import com.vindhyanstudio.catalogue.ProductRepository;
import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.customer.Customer;
import com.vindhyanstudio.customer.CustomerRepository;
import com.vindhyanstudio.invoice.Invoice;
import com.vindhyanstudio.invoice.InvoiceRepository;
import com.vindhyanstudio.order.*;
import com.vindhyanstudio.storage.StorageService;

/**
 * Business numbers for the owner: the admin dashboard and the downloadable Excel report.
 * A "sale" is an order that is confirmed and not cancelled or returned; dates are in IST.
 */
@Service
public class BusinessReportService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<OrderStatus> SOLD = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PRINTING, OrderStatus.READY,
            OrderStatus.DISPATCHED, OrderStatus.DELIVERED);
    private static final int LOW_STOCK = 3;

    private final OrderRepository orders;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final StorageService storage;
    private final AppProperties props;

    public BusinessReportService(OrderRepository orders, ProductRepository products, CustomerRepository customers,
                                 InvoiceRepository invoices, StorageService storage, AppProperties props) {
        this.orders = orders;
        this.products = products;
        this.customers = customers;
        this.invoices = invoices;
        this.storage = storage;
        this.props = props;
    }

    public record Kpis(BigDecimal revenue, int orders, BigDecimal avgOrderValue, int itemsSold, int customers, int newCustomers,
                       int cancelled, int returned, int awaitingPayment, int codOrders, BigDecimal codRevenue,
                       int onlineOrders, BigDecimal onlineRevenue) {}

    public record Point(String label, String date, int orders, BigDecimal revenue) {}

    public record ProductLine(String sku, String name, String category, String image, int qty, BigDecimal revenue) {}

    public record Group(String name, int orders, int qty, BigDecimal revenue) {}

    public record StockItem(String sku, String name, String image, int stock) {}

    public record Attention(long toPrint, long readyToShip, long awaitingPayment, List<StockItem> lowStock, List<StockItem> outOfStock,
                            long liveProducts, long hiddenProducts, long noPhoto, long madeToOrder) {}

    public record Dashboard(String from, String to, String granularity, Kpis kpis, Kpis previous, List<Point> series,
                            List<ProductLine> topProducts, List<Group> categories, List<Group> states, Attention attention) {}

    private record Period(LocalDate from, LocalDate to) {
        OffsetDateTime start() { return from.atStartOfDay(IST).toOffsetDateTime(); }
        OffsetDateTime end() { return to.plusDays(1).atStartOfDay(IST).toOffsetDateTime(); }
        long days() { return ChronoUnit.DAYS.between(from, to) + 1; }
        Period previous() { return new Period(from.minusDays(days()), from.minusDays(1)); }
    }

    // ---------- dashboard ----------

    @Transactional(readOnly = true)
    public Dashboard dashboard(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        List<Order> list = load(p);
        Map<String, Product> catalogue = products.findAll().stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        List<Order> sold = list.stream().filter(o -> SOLD.contains(o.getStatus())).toList();

        boolean monthly = p.days() > 62;
        Map<LocalDate, List<Order>> buckets = new TreeMap<>();
        for (LocalDate d = bucket(p.from(), monthly); !d.isAfter(p.to()); d = monthly ? d.plusMonths(1) : d.plusDays(1)) buckets.put(d, new ArrayList<>());
        sold.forEach(o -> buckets.computeIfAbsent(bucket(o.getCreatedAt().atZoneSameInstant(IST).toLocalDate(), monthly), k -> new ArrayList<>()).add(o));
        DateTimeFormatter label = DateTimeFormatter.ofPattern(monthly ? "MMM yy" : "d MMM");
        List<Point> series = buckets.entrySet().stream()
                .map(e -> new Point(e.getKey().format(label), e.getKey().toString(), e.getValue().size(), sum(e.getValue()))).toList();

        List<ProductLine> top = productLines(sold, catalogue).stream().limit(10).toList();

        Attention attention = new Attention(
                orders.countByStatus(OrderStatus.CONFIRMED) + orders.countByStatus(OrderStatus.PRINTING),
                orders.countByStatus(OrderStatus.READY),
                orders.countByStatus(OrderStatus.PAYMENT_PENDING),
                stock(catalogue.values(), s -> s > 0 && s <= LOW_STOCK),
                stock(catalogue.values(), s -> s <= 0),
                catalogue.values().stream().filter(Product::isActive).count(),
                catalogue.values().stream().filter(x -> !x.isActive()).count(),
                catalogue.values().stream().filter(x -> x.getImages().isEmpty()).count(),
                catalogue.values().stream().filter(x -> x.getStock() >= ProductImportService.MADE_TO_ORDER).count());

        return new Dashboard(p.from().toString(), p.to().toString(), monthly ? "month" : "day",
                kpis(list, p), kpis(load(p.previous()), p.previous()), series, top,
                categoryGroups(sold, catalogue), stateGroups(sold), attention);
    }

    // ---------- Excel report ----------

    @Transactional(readOnly = true)
    public byte[] report(LocalDate from, LocalDate to) throws IOException {
        Period p = period(from, to);
        List<Order> list = load(p);
        List<Order> sold = list.stream().filter(o -> SOLD.contains(o.getStatus())).toList();
        Map<String, Product> catalogue = products.findAll().stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        Map<Long, Customer> people = customers.findAllById(list.stream().map(Order::getCustomerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<Long, String> invoiceNo = invoices.findAll().stream().filter(i -> i.getType() == Invoice.Type.INVOICE)
                .collect(Collectors.toMap(Invoice::getOrderId, Invoice::getInvoiceNo, (a, b) -> a));
        Kpis k = kpis(list, p);
        Kpis prev = kpis(load(p.previous()), p.previous());
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = ReportStyles.header(wb), money = ReportStyles.money(wb), pct = ReportStyles.percent(wb),
                    title = ReportStyles.title(wb), bold = ReportStyles.bold(wb);

            // Summary
            Sheet s = wb.createSheet("Summary");
            int r = 0;
            cell(s.createRow(r++), 0, props.seller().name() + " · Business report", title);
            cell(s.createRow(r++), 0, "Period: " + p.from().format(dmy) + " to " + p.to().format(dmy) + " (" + p.days() + " days)", null);
            cell(s.createRow(r++), 0, "Compared with: " + p.previous().from().format(dmy) + " to " + p.previous().to().format(dmy), null);
            r++;
            Row hr = s.createRow(r++);
            String[] sh = {"Measure", "This period", "Previous period", "Change"};
            for (int i = 0; i < sh.length; i++) cell(hr, i, sh[i], head);
            r = kpiRow(s, r, "Sales (₹)", k.revenue(), prev.revenue(), money, pct);
            r = kpiRow(s, r, "Orders", BigDecimal.valueOf(k.orders()), BigDecimal.valueOf(prev.orders()), null, pct);
            r = kpiRow(s, r, "Average order value (₹)", k.avgOrderValue(), prev.avgOrderValue(), money, pct);
            r = kpiRow(s, r, "Items sold", BigDecimal.valueOf(k.itemsSold()), BigDecimal.valueOf(prev.itemsSold()), null, pct);
            r = kpiRow(s, r, "Customers who ordered", BigDecimal.valueOf(k.customers()), BigDecimal.valueOf(prev.customers()), null, pct);
            r = kpiRow(s, r, "New customers", BigDecimal.valueOf(k.newCustomers()), BigDecimal.valueOf(prev.newCustomers()), null, pct);
            r = kpiRow(s, r, "Cash on delivery orders", BigDecimal.valueOf(k.codOrders()), BigDecimal.valueOf(prev.codOrders()), null, pct);
            r = kpiRow(s, r, "Online paid orders", BigDecimal.valueOf(k.onlineOrders()), BigDecimal.valueOf(prev.onlineOrders()), null, pct);
            r = kpiRow(s, r, "Cancelled orders", BigDecimal.valueOf(k.cancelled()), BigDecimal.valueOf(prev.cancelled()), null, pct);
            r = kpiRow(s, r, "Returned (RTO)", BigDecimal.valueOf(k.returned()), BigDecimal.valueOf(prev.returned()), null, pct);
            r++;
            cell(s.createRow(r++), 0, "Sales = confirmed orders that were not cancelled or returned, including shipping charges and GST.", null);
            cell(s.createRow(r), 0, "Generated " + ZonedDateTime.now(IST).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) + " IST", null);
            widths(s, 34, 18, 18, 12);

            // Daily sales
            Sheet d = wb.createSheet("Daily sales");
            header(d, head, "Date", "Orders", "Items", "Sales (₹)", "Cash on delivery (₹)", "Online (₹)");
            Map<LocalDate, List<Order>> byDay = new TreeMap<>();
            for (LocalDate day = p.from(); !day.isAfter(p.to()); day = day.plusDays(1)) byDay.put(day, new ArrayList<>());
            sold.forEach(o -> byDay.get(o.getCreatedAt().atZoneSameInstant(IST).toLocalDate()).add(o));
            r = 1;
            for (var e : byDay.entrySet()) {
                Row row = d.createRow(r++);
                cell(row, 0, e.getKey().format(dmy), null);
                num(row, 1, e.getValue().size(), null);
                num(row, 2, items(e.getValue()), null);
                num(row, 3, sum(e.getValue()), money);
                num(row, 4, sum(e.getValue().stream().filter(o -> o.getPaymentMode() == PaymentMode.COD).toList()), money);
                num(row, 5, sum(e.getValue().stream().filter(o -> o.getPaymentMode() != PaymentMode.COD).toList()), money);
            }
            totalRow(d, r, bold, money, 1, 5, 3, 4, 5);
            widths(d, 14, 10, 10, 14, 20, 14);
            d.createFreezePane(0, 1);

            // Orders
            Sheet o = wb.createSheet("Orders");
            header(o, head, "Order no", "Date", "Status", "Customer", "Phone", "Email", "City", "State", "Pincode", "Items",
                    "Item value (₹)", "Shipping (₹)", "Total (₹)", "Payment", "Invoice no");
            r = 1;
            for (Order x : list) {
                AddressSnapshot a = x.address();
                Customer c = people.get(x.getCustomerId());
                Row row = o.createRow(r++);
                cell(row, 0, x.getOrderNo(), null);
                cell(row, 1, x.getCreatedAt().atZoneSameInstant(IST).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")), null);
                cell(row, 2, statusLabel(x.getStatus()), null);
                cell(row, 3, a.name(), null);
                cell(row, 4, a.phone(), null);
                cell(row, 5, c == null ? "" : Objects.toString(c.getEmail(), ""), null);
                cell(row, 6, a.city(), null);
                cell(row, 7, a.state(), null);
                cell(row, 8, a.pincode(), null);
                num(row, 9, x.getItems().stream().mapToInt(OrderItem::getQty).sum(), null);
                num(row, 10, x.getSubtotal(), money);
                num(row, 11, x.getShippingFee(), money);
                num(row, 12, x.getTotal(), money);
                cell(row, 13, x.getPaymentMode() == PaymentMode.COD ? "Cash on delivery" : "Online", null);
                cell(row, 14, invoiceNo.getOrDefault(x.getId(), ""), null);
            }
            widths(o, 12, 17, 18, 22, 13, 26, 16, 16, 9, 7, 14, 12, 12, 17, 18);
            o.createFreezePane(1, 1);
            if (r > 1) o.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, r - 1, 0, 14));

            // Products sold
            Sheet ps = wb.createSheet("Products sold");
            header(ps, head, "SKU", "Product", "Category", "Qty sold", "Sales (₹)", "Average price (₹)", "Stock now");
            r = 1;
            for (ProductLine l : productLines(sold, catalogue)) {
                Row row = ps.createRow(r++);
                cell(row, 0, l.sku(), null);
                cell(row, 1, l.name(), null);
                cell(row, 2, l.category(), null);
                num(row, 3, l.qty(), null);
                num(row, 4, l.revenue(), money);
                num(row, 5, l.qty() == 0 ? BigDecimal.ZERO : l.revenue().divide(BigDecimal.valueOf(l.qty()), 2, RoundingMode.HALF_UP), money);
                Product cur = catalogue.get(l.sku());
                cell(row, 6, cur == null ? "removed" : ProductImportService.stockLabel(cur.getStock()), null);
            }
            totalRow(ps, r, bold, money, 3, 4, 4);
            widths(ps, 12, 36, 18, 10, 14, 18, 14);
            ps.createFreezePane(0, 1);

            // Category sales
            Sheet cs = wb.createSheet("Category sales");
            header(cs, head, "Category", "Orders", "Qty sold", "Sales (₹)", "Share of sales");
            BigDecimal itemTotal = sold.stream().flatMap(x -> x.getItems().stream()).map(this::line).reduce(BigDecimal.ZERO, BigDecimal::add);
            r = 1;
            for (Group g : categoryGroups(sold, catalogue)) {
                Row row = cs.createRow(r++);
                cell(row, 0, g.name(), null);
                num(row, 1, g.orders(), null);
                num(row, 2, g.qty(), null);
                num(row, 3, g.revenue(), money);
                Cell share = row.createCell(4);
                share.setCellValue(itemTotal.signum() == 0 ? 0 : g.revenue().divide(itemTotal, 4, RoundingMode.HALF_UP).doubleValue());
                share.setCellStyle(pct);
            }
            widths(cs, 22, 10, 10, 14, 14);

            // GST
            Sheet g = wb.createSheet("GST summary");
            header(g, head, "Invoice no", "Date", "Order no", "Customer state", "Supply", "Taxable value (₹)", "CGST (₹)", "SGST (₹)", "IGST (₹)", "Invoice total (₹)");
            r = 1;
            String sellerState = Objects.toString(props.seller().state(), "");
            for (Order x : list.stream().filter(z -> SOLD.contains(z.getStatus()) || z.getStatus() == OrderStatus.RTO).toList()) {
                BigDecimal[] t = gst(x);
                boolean intra = sellerState.equalsIgnoreCase(Objects.toString(x.address().state(), ""));
                Row row = g.createRow(r++);
                cell(row, 0, invoiceNo.getOrDefault(x.getId(), ""), null);
                cell(row, 1, x.getCreatedAt().atZoneSameInstant(IST).format(dmy), null);
                cell(row, 2, x.getOrderNo(), null);
                cell(row, 3, x.address().state(), null);
                cell(row, 4, intra ? "Within state (CGST+SGST)" : "Other state (IGST)", null);
                num(row, 5, t[0], money);
                BigDecimal half = t[1].divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                num(row, 6, intra ? half : BigDecimal.ZERO, money);
                num(row, 7, intra ? t[1].subtract(half) : BigDecimal.ZERO, money);
                num(row, 8, intra ? BigDecimal.ZERO : t[1], money);
                num(row, 9, x.getTotal(), money);
            }
            totalRow(g, r, bold, money, 5, 9, 5, 6, 7, 8, 9);
            Row note = g.createRow(r + 2);
            cell(note, 0, "Prices include GST; taxable value is worked back from each item's GST rate. Shipping is taxed at the main item's rate. Check with your CA before filing.", null);
            widths(g, 20, 12, 12, 18, 24, 18, 12, 12, 12, 18);
            g.createFreezePane(0, 1);

            // Stock
            Sheet st = wb.createSheet("Stock now");
            header(st, head, "SKU", "Product", "Category", "Selling price (₹)", "MRP (₹)", "Stock", "On website", "Photos");
            r = 1;
            for (Product x : catalogue.values().stream().sorted(Comparator.comparing((Product y) -> y.getCategory().getName()).thenComparing(Product::getSku)).toList()) {
                Row row = st.createRow(r++);
                cell(row, 0, x.getSku(), null);
                cell(row, 1, x.getName(), null);
                cell(row, 2, x.getCategory().getName(), null);
                num(row, 3, x.getSellingPrice(), money);
                num(row, 4, x.getMrp(), money);
                cell(row, 5, ProductImportService.stockLabel(x.getStock()), null);
                cell(row, 6, x.isActive() ? "Yes" : "No", null);
                num(row, 7, x.getImages().size(), null);
            }
            widths(st, 12, 36, 18, 16, 12, 14, 11, 8);
            st.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---------- calculations ----------

    private Kpis kpis(List<Order> list, Period p) {
        List<Order> sold = list.stream().filter(o -> SOLD.contains(o.getStatus())).toList();
        BigDecimal revenue = sum(sold);
        List<Order> cod = sold.stream().filter(o -> o.getPaymentMode() == PaymentMode.COD).toList();
        List<Order> online = sold.stream().filter(o -> o.getPaymentMode() != PaymentMode.COD).toList();
        int newCustomers = (int) customers.findAll().stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(p.start()) && c.getCreatedAt().isBefore(p.end())).count();
        return new Kpis(revenue, sold.size(),
                sold.isEmpty() ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(sold.size()), 2, RoundingMode.HALF_UP),
                items(sold), (int) sold.stream().map(Order::getCustomerId).distinct().count(), newCustomers,
                count(list, OrderStatus.CANCELLED), count(list, OrderStatus.RTO), count(list, OrderStatus.PAYMENT_PENDING),
                cod.size(), sum(cod), online.size(), sum(online));
    }

    private List<ProductLine> productLines(List<Order> sold, Map<String, Product> catalogue) {
        Map<String, int[]> qty = new HashMap<>();
        Map<String, BigDecimal> rev = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Order o : sold) for (OrderItem i : o.getItems()) {
            qty.computeIfAbsent(i.getSku(), k -> new int[1])[0] += i.getQty();
            rev.merge(i.getSku(), line(i), BigDecimal::add);
            names.putIfAbsent(i.getSku(), i.getName());
        }
        return rev.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).map(e -> {
            Product cur = catalogue.get(e.getKey());
            String image = cur == null || cur.getImages().isEmpty() ? null : storage.publicUrl(cur.getImages().get(0).getStorageKey());
            return new ProductLine(e.getKey(), names.get(e.getKey()), cur == null ? "" : cur.getCategory().getName(), image, qty.get(e.getKey())[0], e.getValue());
        }).toList();
    }

    private List<Group> categoryGroups(List<Order> sold, Map<String, Product> catalogue) {
        Map<String, Set<Long>> orderIds = new HashMap<>();
        Map<String, int[]> qty = new HashMap<>();
        Map<String, BigDecimal> rev = new HashMap<>();
        for (Order o : sold) for (OrderItem i : o.getItems()) {
            Product cur = catalogue.get(i.getSku());
            String cat = cur == null ? "Other" : cur.getCategory().getName();
            orderIds.computeIfAbsent(cat, k -> new HashSet<>()).add(o.getId());
            qty.computeIfAbsent(cat, k -> new int[1])[0] += i.getQty();
            rev.merge(cat, line(i), BigDecimal::add);
        }
        return rev.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new Group(e.getKey(), orderIds.get(e.getKey()).size(), qty.get(e.getKey())[0], e.getValue())).toList();
    }

    private List<Group> stateGroups(List<Order> sold) {
        return sold.stream().collect(Collectors.groupingBy(o -> Objects.toString(o.address().state(), "Unknown"))).entrySet().stream()
                .map(e -> new Group(e.getKey(), e.getValue().size(), items(e.getValue()), sum(e.getValue())))
                .sorted(Comparator.comparing(Group::revenue).reversed()).limit(8).toList();
    }

    /** [taxable value, total GST] for an order; prices are GST-inclusive. */
    private BigDecimal[] gst(Order o) {
        BigDecimal taxable = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        OrderItem main = o.getItems().stream().max(Comparator.comparing(this::line)).orElse(null);
        for (OrderItem i : o.getItems()) {
            BigDecimal[] t = split(line(i), i.getGstRate());
            taxable = taxable.add(t[0]);
            tax = tax.add(t[1]);
        }
        if (o.getShippingFee() != null && o.getShippingFee().signum() > 0 && main != null) {
            BigDecimal[] t = split(o.getShippingFee(), main.getGstRate());
            taxable = taxable.add(t[0]);
            tax = tax.add(t[1]);
        }
        return new BigDecimal[] {taxable, tax};
    }

    private static BigDecimal[] split(BigDecimal gross, BigDecimal rate) {
        BigDecimal r = rate == null ? BigDecimal.ZERO : rate;
        BigDecimal taxable = gross.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(100).add(r), 2, RoundingMode.HALF_UP);
        return new BigDecimal[] {taxable, gross.subtract(taxable)};
    }

    private List<StockItem> stock(Collection<Product> all, java.util.function.IntPredicate test) {
        return all.stream().filter(Product::isActive).filter(x -> test.test(x.getStock()))
                .sorted(Comparator.comparingInt(Product::getStock))
                .map(x -> new StockItem(x.getSku(), x.getName(), x.getImages().isEmpty() ? null : storage.publicUrl(x.getImages().get(0).getStorageKey()), x.getStock()))
                .limit(20).toList();
    }

    private List<Order> load(Period p) {
        return orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(p.start(), p.end());
    }

    private static Period period(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(IST);
        LocalDate f = from == null ? today.minusDays(29) : from;
        LocalDate t = to == null ? today : to;
        if (t.isBefore(f)) throw ApiException.badRequest("The end date is before the start date");
        if (ChronoUnit.DAYS.between(f, t) > 3 * 366) throw ApiException.badRequest("Choose a period of 3 years or less");
        return new Period(f, t);
    }

    private static LocalDate bucket(LocalDate d, boolean monthly) {
        return monthly ? d.withDayOfMonth(1) : d;
    }

    private BigDecimal line(OrderItem i) {
        return i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQty()));
    }

    private static BigDecimal sum(List<Order> list) {
        return list.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int items(List<Order> list) {
        return list.stream().flatMap(o -> o.getItems().stream()).mapToInt(OrderItem::getQty).sum();
    }

    private static int count(List<Order> list, OrderStatus s) {
        return (int) list.stream().filter(o -> o.getStatus() == s).count();
    }

    static String statusLabel(OrderStatus s) {
        return switch (s) {
            case PAYMENT_PENDING -> "Awaiting payment";
            case CONFIRMED -> "Confirmed";
            case PRINTING -> "Printing";
            case READY -> "Packed";
            case DISPATCHED -> "Shipped";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case RTO -> "Returned (RTO)";
        };
    }

    // ---------- sheet helpers ----------

    private static int kpiRow(Sheet s, int r, String label, BigDecimal now, BigDecimal before, CellStyle fmt, CellStyle pct) {
        Row row = s.createRow(r);
        cell(row, 0, label, null);
        num(row, 1, now, fmt);
        num(row, 2, before, fmt);
        Cell c = row.createCell(3);
        if (before.signum() != 0) {
            c.setCellValue(now.subtract(before).divide(before, 4, RoundingMode.HALF_UP).doubleValue());
            c.setCellStyle(pct);
        } else c.setCellValue(now.signum() == 0 ? "" : "new");
        return r + 1;
    }

    private static void header(Sheet s, CellStyle style, String... names) {
        Row r = s.createRow(0);
        for (int i = 0; i < names.length; i++) cell(r, i, names[i], style);
    }

    /** "Total" row summing columns firstCol..lastCol (only the listed money columns get the ₹ format). */
    private static void totalRow(Sheet s, int r, CellStyle bold, CellStyle money, int firstCol, int lastCol, int... moneyCols) {
        if (r <= 1) return;
        Row row = s.createRow(r);
        cell(row, 0, "Total", bold);
        Set<Integer> m = Arrays.stream(moneyCols).boxed().collect(Collectors.toSet());
        for (int c = firstCol; c <= lastCol; c++) {
            String col = org.apache.poi.ss.util.CellReference.convertNumToColString(c);
            Cell cell = row.createCell(c);
            cell.setCellFormula("SUM(" + col + "2:" + col + r + ")");
            if (m.contains(c)) cell.setCellStyle(money);
        }
    }

    private static void widths(Sheet s, int... w) {
        for (int i = 0; i < w.length; i++) s.setColumnWidth(i, w[i] * 256);
    }

    private static void cell(Row r, int i, String v, CellStyle style) {
        Cell c = r.createCell(i);
        c.setCellValue(v == null ? "" : v);
        if (style != null) c.setCellStyle(style);
    }

    private static void num(Row r, int i, Number v, CellStyle style) {
        Cell c = r.createCell(i);
        c.setCellValue(v == null ? 0 : v.doubleValue());
        if (style != null) c.setCellStyle(style);
    }
}
