/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.admin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vindhyanstudio.catalogue.*;
import com.vindhyanstudio.catalogue.sync.CatalogueSource;
import com.vindhyanstudio.catalogue.sync.CatalogueSyncService;
import com.vindhyanstudio.catalogue.sync.LocalExcelCatalogueSource;
import com.vindhyanstudio.catalogue.sync.SyncRun;
import com.vindhyanstudio.catalogue.sync.SyncRunRepository;
import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.storage.StorageService;

/**
 * Owner-facing Excel workflow: download the product list, edit it in Excel, upload it, review every
 * change, then apply. Blank cells keep the current value, so a sheet with just SKU + Stock is a stock update.
 * Nothing changes until {@link #apply} is called with the rows the owner ticked.
 */
@Service
public class ProductImportService {

    public static final int MADE_TO_ORDER = CatalogueSyncService.MADE_TO_ORDER_STOCK;
    private static final String[] HEADERS = {"SKU", "Product name", "Category", "MRP (₹)", "Selling price (₹)", "Stock", "Show on website", "Description"};

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SyncRunRepository runs;
    private final StorageService storage;

    public ProductImportService(ProductRepository products, CategoryRepository categories, SyncRunRepository runs, StorageService storage) {
        this.products = products;
        this.categories = categories;
        this.runs = runs;
        this.storage = storage;
    }

    public record Change(String field, String label, String from, String to) {}

    public record RowResult(int row, String sku, String name, String image, String status,
                            List<Change> changes, List<String> errors, List<String> warnings) {}

    public record Preview(int newCount, int updateCount, int sameCount, int errorCount, List<String> newCategories, List<RowResult> rows) {}

    public record Applied(int created, int updated, int skipped) {}

    /** Parsed sheet row, with nulls for "keep the current value". */
    private record Parsed(int row, String sku, String name, String category, BigDecimal mrp, BigDecimal price,
                          Integer stock, Boolean active, String description, List<String> errors, List<String> warnings) {}

    // ---------- review ----------

    @Transactional(readOnly = true)
    public Preview preview(byte[] file) {
        List<Parsed> rows = parse(file);
        Map<String, Product> existing = products.findAll().stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        Set<String> knownCats = categories.findAll().stream().map(c -> c.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> newCats = new TreeSet<>();
        List<RowResult> out = new ArrayList<>();
        int[] n = new int[4];

        for (Parsed r : rows) {
            Product p = existing.get(r.sku());
            List<String> warnings = new ArrayList<>(r.warnings());
            if (r.category() != null && !knownCats.contains(r.category().toLowerCase(Locale.ROOT))) {
                newCats.add(r.category());
                warnings.add("New category \"" + r.category() + "\" will be created");
            }
            List<String> errors = new ArrayList<>(r.errors());
            if (p == null) {
                if (r.name() == null) errors.add("Product name is needed for a new product");
                if (r.category() == null) errors.add("Category is needed for a new product");
                if (r.mrp() == null || r.price() == null) errors.add("MRP and selling price are needed for a new product");
            }
            BigDecimal mrp = r.mrp() != null ? r.mrp() : p == null ? null : p.getMrp();
            BigDecimal price = r.price() != null ? r.price() : p == null ? null : p.getSellingPrice();
            if (mrp != null && price != null && price.compareTo(mrp) > 0) errors.add("Selling price is higher than MRP");
            if (p != null && r.price() != null && p.getSellingPrice().signum() > 0) {
                BigDecimal ratio = r.price().divide(p.getSellingPrice(), 2, RoundingMode.HALF_UP);
                if (ratio.compareTo(new BigDecimal("0.5")) < 0 || ratio.compareTo(new BigDecimal("2")) > 0) {
                    warnings.add("Price changes a lot (" + rupees(p.getSellingPrice()) + " → " + rupees(r.price()) + "). Please double-check");
                }
            }

            List<Change> changes = new ArrayList<>();
            String status;
            if (!errors.isEmpty()) {
                status = "ERROR";
                n[3]++;
            } else if (p == null) {
                status = "NEW";
                n[0]++;
                changes.add(new Change("price", "Price", null, rupees(price) + (mrp.compareTo(price) > 0 ? " (MRP " + rupees(mrp) + ")" : "")));
                changes.add(new Change("stock", "Stock", null, stockLabel(r.stock() == null ? MADE_TO_ORDER : r.stock())));
                warnings.add("New products stay hidden until you add photos in Products");
            } else {
                diff(changes, "name", "Name", p.getName(), r.name());
                diff(changes, "category", "Category", p.getCategory().getName(), r.category());
                if (r.mrp() != null && r.mrp().compareTo(p.getMrp()) != 0) changes.add(new Change("mrp", "MRP", rupees(p.getMrp()), rupees(r.mrp())));
                if (r.price() != null && r.price().compareTo(p.getSellingPrice()) != 0) changes.add(new Change("price", "Price", rupees(p.getSellingPrice()), rupees(r.price())));
                if (r.stock() != null && r.stock() != p.getStock()) changes.add(new Change("stock", "Stock", stockLabel(p.getStock()), stockLabel(r.stock())));
                if (r.active() != null && r.active() != p.isActive()) {
                    changes.add(new Change("active", "On website", p.isActive() ? "Yes" : "No", r.active() ? "Yes" : "No"));
                    if (r.active() && p.getImages().isEmpty()) warnings.add("Has no photos, so it will stay hidden");
                }
                if (r.description() != null && !r.description().equals(Objects.toString(p.getDescription(), "").trim())) {
                    changes.add(new Change("description", "Description", "old text", "new text"));
                }
                status = changes.isEmpty() ? "SAME" : "UPDATE";
                n[status.equals("SAME") ? 2 : 1]++;
            }
            String name = r.name() != null ? r.name() : p == null ? "" : p.getName();
            String image = p == null || p.getImages().isEmpty() ? null : storage.publicUrl(p.getImages().get(0).getStorageKey());
            out.add(new RowResult(r.row(), r.sku(), name, image, status, changes, errors, warnings));
        }
        // Problems first, then new products, then changes; unchanged rows last.
        List<String> order = List.of("ERROR", "NEW", "UPDATE", "SAME");
        out.sort(Comparator.comparingInt((RowResult r) -> order.indexOf(r.status())).thenComparingInt(RowResult::row));
        return new Preview(n[0], n[1], n[2], n[3], List.copyOf(newCats), out);
    }

    // ---------- apply ----------

    @Transactional
    public Applied apply(byte[] file, Set<String> selectedSkus, String by) {
        Preview preview = preview(file);
        Map<String, RowResult> ok = preview.rows().stream()
                .filter(r -> r.status().equals("NEW") || r.status().equals("UPDATE"))
                .filter(r -> selectedSkus == null || selectedSkus.contains(r.sku()))
                .collect(Collectors.toMap(RowResult::sku, Function.identity(), (a, b) -> a));
        Map<String, Category> cats = new HashMap<>();
        categories.findAll().forEach(c -> cats.put(c.getName().toLowerCase(Locale.ROOT), c));
        OffsetDateTime now = OffsetDateTime.now();
        int created = 0, updated = 0;
        List<SyncRun.Issue> log = new ArrayList<>();

        for (Parsed r : parse(file)) {
            RowResult res = ok.get(r.sku());
            if (res == null) continue;
            Product p = products.findById(r.sku()).orElse(null);
            boolean isNew = p == null;
            if (isNew) {
                p = new Product(r.sku());
                p.setCustomisationSpec(null);
            }
            if (r.name() != null) p.setName(r.name());
            if (r.category() != null) p.setCategory(cats.computeIfAbsent(r.category().toLowerCase(Locale.ROOT), k -> newCategory(r.category(), cats.size())));
            if (r.description() != null) p.setDescription(r.description());
            if (r.mrp() != null) p.setMrp(r.mrp());
            if (r.price() != null) p.setSellingPrice(r.price());
            p.setOfferPct(p.getMrp().signum() == 0 ? 0 : p.getMrp().subtract(p.getSellingPrice()).multiply(BigDecimal.valueOf(100))
                    .divide(p.getMrp(), 0, RoundingMode.HALF_UP).intValue());
            if (r.stock() != null) p.setStock(r.stock());
            else if (isNew) p.setStock(MADE_TO_ORDER);
            boolean wantLive = r.active() != null ? r.active() : !isNew || p.isActive();
            p.setActive(wantLive && !p.getImages().isEmpty());
            p.setSource("ADMIN");
            p.setSyncedAt(now);
            p.setSlug(CatalogueSyncService.slugify(p.getName()) + "-" + p.getSku().toLowerCase(Locale.ROOT));
            products.save(p);
            if (isNew) created++;
            else updated++;
            log.add(new SyncRun.Issue("INFO", r.row(), r.sku(), (isNew ? "Added: " : "Updated: ")
                    + res.changes().stream().map(c -> c.label() + (c.to() == null ? "" : " " + c.to())).collect(Collectors.joining(", "))));
        }
        int skipped = preview.rows().size() - created - updated - preview.sameCount();
        SyncRun run = new SyncRun();
        log.add(0, new SyncRun.Issue("INFO", 0, null, "Excel upload by " + by));
        run.finish(created + updated, skipped, log);
        runs.save(run);
        return new Applied(created, updated, skipped);
    }

    // ---------- download ----------

    /** The current product list as an Excel file the owner can edit and upload back. */
    @Transactional(readOnly = true)
    public byte[] exportProducts() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = ReportStyles.header(wb);
            CellStyle money = ReportStyles.money(wb);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(false);

            Sheet sh = wb.createSheet("products");
            Row h = sh.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(head);
            }
            List<Product> list = products.findAll().stream()
                    .sorted(Comparator.comparing((Product p) -> p.getCategory().getName()).thenComparing(Product::getSku)).toList();
            int r = 1;
            for (Product p : list) {
                Row row = sh.createRow(r++);
                row.createCell(0).setCellValue(p.getSku());
                row.createCell(1).setCellValue(p.getName());
                row.createCell(2).setCellValue(p.getCategory().getName());
                Cell mrp = row.createCell(3); mrp.setCellValue(p.getMrp().doubleValue()); mrp.setCellStyle(money);
                Cell price = row.createCell(4); price.setCellValue(p.getSellingPrice().doubleValue()); price.setCellStyle(money);
                if (p.getStock() >= MADE_TO_ORDER) row.createCell(5).setCellValue("Made to order");
                else row.createCell(5).setCellValue(p.getStock());
                row.createCell(6).setCellValue(p.isActive() ? "Yes" : "No");
                row.createCell(7).setCellValue(Objects.toString(p.getDescription(), ""));
            }
            int[] widths = {12, 34, 18, 12, 16, 15, 16, 60};
            for (int i = 0; i < widths.length; i++) sh.setColumnWidth(i, widths[i] * 256);
            sh.createFreezePane(2, 1);
            sh.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, r - 1), 0, HEADERS.length - 1));

            // Drop-downs so the owner can't mistype Yes/No or a category name.
            Sheet catSheet = wb.createSheet("categories");
            Row ch = catSheet.createRow(0);
            Cell chc = ch.createCell(0); chc.setCellValue("Category"); chc.setCellStyle(head);
            List<Category> cats = categories.findAllByOrderBySortOrderAsc();
            for (int i = 0; i < cats.size(); i++) catSheet.createRow(i + 1).createCell(0).setCellValue(cats.get(i).getName());
            catSheet.setColumnWidth(0, 30 * 256);
            DataValidationHelper dv = sh.getDataValidationHelper();
            int last = Math.max(r + 500, 1000);
            DataValidation yesNo = dv.createValidation(dv.createExplicitListConstraint(new String[] {"Yes", "No"}), new CellRangeAddressList(1, last, 6, 6));
            yesNo.setShowErrorBox(true);
            sh.addValidationData(yesNo);
            if (!cats.isEmpty()) {
                DataValidation catList = dv.createValidation(dv.createFormulaListConstraint("categories!$A$2:$A$" + (cats.size() + 1)),
                        new CellRangeAddressList(1, last, 2, 2));
                catList.setShowErrorBox(false); // a new name is allowed: it creates a new category
                sh.addValidationData(catList);
            }

            Sheet help = wb.createSheet("how to use");
            String[] lines = {
                    "How to update products with this file",
                    "",
                    "1. Change prices, stock, names or descriptions in the \"products\" sheet.",
                    "2. To add a product, add a new row with a new SKU (for example VS-120). New products stay hidden until you add photos on the website.",
                    "3. Stock: type a number, or \"Made to order\" for items you print after the order comes in.",
                    "4. Show on website: Yes or No. Use No to hide a product (nothing is deleted).",
                    "5. Leave a cell empty to keep what is on the website now.",
                    "   Tip: a sheet with only the SKU and Stock columns updates just the stock.",
                    "6. Save the file, then go to Admin → Update from Excel and upload it.",
                    "7. You'll see every change before anything goes live. Tick what you want and click Apply.",
                    "",
                    "Don't change the SKU of an existing product: the SKU is how the website recognises it.",
            };
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.getRow(0).getCell(0).setCellStyle(head);
            help.setColumnWidth(0, 120 * 256);
            wb.setActiveSheet(0);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---------- parsing ----------

    private List<Parsed> parse(byte[] file) {
        CatalogueSource.Snapshot snap;
        try {
            snap = LocalExcelCatalogueSource.parse(new ByteArrayInputStream(file));
        } catch (Exception e) {
            throw ApiException.badRequest("This doesn't look like an Excel file (.xlsx). Download the product list, edit it and upload that file.");
        }
        if (snap.products().isEmpty()) throw ApiException.badRequest("The sheet is empty.");
        CatalogueSource.Row first = snap.products().get(0);
        if (!first.has("sku") && !first.has("s no")) {
            throw ApiException.badRequest("The first row must have column names, including \"SKU\". Download the product list to get the right format.");
        }

        List<Parsed> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CatalogueSource.Row row : snap.products()) {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            // Our layout ("SKU") or the owner's original sheet ("S no." → VS-001).
            String sku = row.any("sku", "sku_id").toUpperCase(Locale.ROOT);
            if (sku.isEmpty() && row.has("s no")) {
                String serial = row.any("s no").replaceAll("\\.0+$", "");
                if (!serial.matches("\\d+")) continue;
                sku = String.format("VS-%03d", Integer.parseInt(serial));
            }
            if (sku.isEmpty()) continue; // blank / notes rows
            if (!sku.matches("[A-Z0-9_-]{1,50}")) errors.add("SKU may only contain letters, numbers, - and _");
            else if (!seen.add(sku)) errors.add("This SKU appears more than once in the file");

            String name = blank(row.any("product name", "product_name"));
            if (name == null && row.has("sub-category")) name = blank(productName(row.any("sub-category"), row.any("category")));
            String category = blank(row.any("category", "product_category"));
            BigDecimal mrp = money(row.any("mrp"), "MRP", errors);
            BigDecimal price = money(row.any("selling price", "selling_price"), "Selling price", errors);

            Integer stock = null;
            String stockRaw = row.any("stock").trim();
            if (!stockRaw.isEmpty()) {
                String s = stockRaw.toLowerCase(Locale.ROOT);
                if (s.contains("order") || s.equals("mto") || s.equals("∞")) stock = MADE_TO_ORDER;
                else {
                    try {
                        stock = new BigDecimal(stockRaw.replace(",", "")).intValueExact();
                        if (stock < 0) errors.add("Stock can't be negative");
                        if (stock >= MADE_TO_ORDER) stock = MADE_TO_ORDER;
                    } catch (ArithmeticException | NumberFormatException e) {
                        errors.add("Stock must be a whole number or \"Made to order\" (found \"" + stockRaw + "\")");
                    }
                }
            }

            Boolean active = null;
            String live = row.any("show on website", "flag").trim().toLowerCase(Locale.ROOT);
            if (!live.isEmpty()) {
                if (List.of("yes", "y", "1", "true", "live", "show").contains(live)) active = true;
                else if (List.of("no", "n", "0", "false", "hidden", "hide").contains(live)) active = false;
                else warnings.add("\"Show on website\" should be Yes or No; kept as it is");
            }
            String desc = blank(row.any("description", "descriptions"));
            if (desc != null && desc.length() > 3900) {
                desc = desc.substring(0, 3900);
                warnings.add("Description was too long and has been shortened");
            }
            if (name != null && name.length() > 200) errors.add("Product name is too long (max 200 characters)");
            if (category != null && category.length() > 100) errors.add("Category name is too long");
            out.add(new Parsed(row.rowNumber(), sku, name, category, mrp, price, stock, active, desc, errors, warnings));
        }
        if (out.isEmpty()) throw ApiException.badRequest("No product rows found. Each row needs a SKU.");
        return out;
    }

    private Category newCategory(String name, int order) {
        Category c = new Category();
        c.setName(name.trim());
        c.setSlug(CatalogueSyncService.slugify(name));
        c.setSortOrder(order);
        return categories.save(c);
    }

    private static void diff(List<Change> changes, String field, String label, String from, String to) {
        if (to != null && !to.equals(from)) changes.add(new Change(field, label, from, to));
    }

    private static String productName(String sub, String category) {
        String s = sub.trim();
        if (s.isEmpty()) return category.trim();
        if (s.toLowerCase(Locale.ROOT).matches("custom(isable|ized|ised)?")) s = s + " " + category.trim();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal money(String raw, String label, List<String> errors) {
        String s = raw.replace("₹", "").replace(",", "").replace("Rs.", "").trim();
        if (s.isEmpty()) return null;
        try {
            BigDecimal v = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            if (v.signum() <= 0) {
                errors.add(label + " must be more than 0");
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            errors.add(label + " is not a number (\"" + raw + "\")");
            return null;
        }
    }

    static String stockLabel(int stock) {
        return stock >= MADE_TO_ORDER ? "Made to order" : String.valueOf(stock);
    }

    static String rupees(BigDecimal v) {
        return "₹" + v.stripTrailingZeros().toPlainString();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
