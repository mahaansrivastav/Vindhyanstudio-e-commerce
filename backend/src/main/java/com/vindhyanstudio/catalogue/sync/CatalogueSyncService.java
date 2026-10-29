package com.vindhyanstudio.catalogue.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.vindhyanstudio.catalogue.*;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.storage.StorageService;

/**
 * Pulls the owner's spreadsheet + photo folders into the database (BRD FR-07, FR-26).
 * Bad rows are skipped and reported; products no longer in the sheet are hidden, never deleted.
 */
@Service
public class CatalogueSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSyncService.class);

    private final CatalogueSource source;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final SyncRunRepository runs;
    private final StorageService storage;
    private final TransactionTemplate tx;
    private final AppProperties props;

    public CatalogueSyncService(CatalogueSource source, CategoryRepository categories, ProductRepository products,
                                SyncRunRepository runs, StorageService storage, TransactionTemplate tx, AppProperties props) {
        this.source = source;
        this.categories = categories;
        this.products = products;
        this.runs = runs;
        this.storage = storage;
        this.tx = tx;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup(ApplicationReadyEvent event) {
        if (props.catalogue().publishOnly()) {
            // scripts/publish-catalogue.sh: sync once into the configured (production) DB + storage, print the report, exit.
            SyncRun run = sync();
            System.out.println("\n=== Catalogue published: " + run.getRowsOk() + " live, " + run.getRowsFailed() + " skipped ===");
            run.getIssues().forEach(i -> System.out.println("  " + i.severity() + " row " + i.row() + " " + (i.sku() == null ? "" : i.sku()) + ": " + i.message()));
            System.exit(org.springframework.boot.SpringApplication.exit(event.getApplicationContext(), () -> 0));
        }
        if (props.catalogue().syncOnStartup()) safeSync();
    }

    @Scheduled(cron = "${app.catalogue.sync-cron}")
    public void scheduled() {
        safeSync();
    }

    private void safeSync() {
        try {
            SyncRun run = sync();
            log.info("Catalogue sync: {} ok, {} skipped", run.getRowsOk(), run.getRowsFailed());
        } catch (Exception e) {
            log.error("Catalogue sync failed", e);
        }
    }

    public synchronized SyncRun sync() {
        SyncRun run = new SyncRun();
        List<SyncRun.Issue> issues = new ArrayList<>();
        int[] counts = {0, 0};
        try {
            CatalogueSource.Snapshot snap = source.read();
            tx.executeWithoutResult(s -> apply(snap, issues, counts));
        } catch (Exception e) {
            issues.add(new SyncRun.Issue("ERROR", 0, null, "Could not read catalogue: " + e.getMessage()));
        }
        run.finish(counts[0], counts[1], issues);
        return runs.save(run);
    }

    private void apply(CatalogueSource.Snapshot snap, List<SyncRun.Issue> issues, int[] counts) {
        Map<String, Category> cats = syncCategories(snap.categories(), issues);
        Map<String, Product> existing = products.findAll().stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        Set<String> seen = new HashSet<>();
        Set<String> usedSlugs = new HashSet<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (CatalogueSource.Row row : snap.products()) {
            // Two sheet layouts are supported: our template (SKU_id, Product_Name, Product_category…)
            // and the owner's working sheet (S no., Flag, Category, Sub-Category, MRP (₹)…, Image File).
            String sku = row.any("SKU_id");
            if (sku.isEmpty() && row.has("s no")) {
                String serial = row.any("s no").replaceAll("\\.0+$", "");
                if (!serial.matches("\\d+")) continue; // totals / notes rows
                sku = String.format("VS-%03d", Integer.parseInt(serial));
            }
            List<String> errors = new ArrayList<>();
            if (sku.isEmpty()) errors.add("SKU_id is empty");
            else if (!sku.matches("[A-Za-z0-9_-]{1,50}")) errors.add("SKU_id may only contain letters, digits, - and _");
            else if (!seen.add(sku)) errors.add("Duplicate SKU_id");

            Product owned = existing.get(sku);
            if (owned != null && "ADMIN".equals(owned.getSource())) {
                errors.add("SKU is managed in the admin screen; change the SKU in the sheet or remove this row");
            }

            if (row.any("flag").equals("0")) {
                // Owner's "don't put on website" switch: hide it (the product stays in the database).
                issues.add(new SyncRun.Issue("INFO", row.rowNumber(), sku, "Hidden (Flag = 0)"));
                seen.remove(sku);
                continue;
            }

            String catName = row.any("Product_category", "category");
            String name = row.any("Product_Name");
            if (name.isEmpty()) name = productName(row.any("sub-category", "subcategory"), catName);
            if (name.isEmpty()) errors.add("Product name is empty");

            Category cat = cats.get(catName.toLowerCase());
            if (cat == null && !catName.isEmpty() && snap.categories().isEmpty()) {
                cat = autoCategory(catName, cats);
            }
            if (cat == null) errors.add(catName.isEmpty() ? "Category is empty" : "Unknown category '" + catName + "'");

            BigDecimal mrp = money(row.any("MRP"), "MRP", errors);
            BigDecimal price = money(row.any("Selling_Price", "selling price"), "Selling price", errors);
            if (mrp != null && price != null && price.compareTo(mrp) > 0) errors.add("Selling price is higher than MRP");

            // No stock column = everything is printed to order, so never "out of stock".
            int stock = row.has("stock") ? 0 : MADE_TO_ORDER_STOCK;
            String stockRaw = row.any("stock");
            if (!stockRaw.isEmpty()) {
                try {
                    stock = new BigDecimal(stockRaw).intValueExact();
                    if (stock < 0) errors.add("stock cannot be negative");
                } catch (ArithmeticException | NumberFormatException e) {
                    errors.add("stock must be a whole number");
                }
            }

            List<CatalogueSource.Photo> photos = new ArrayList<>();
            if (errors.isEmpty()) {
                String imageFile = row.any("image file", "image");
                try {
                    if (!imageFile.isEmpty()) {
                        CatalogueSource.Photo main = source.file(imageFile);
                        if (main == null) errors.add("Image file not found: " + imageFile);
                        else photos.add(main);
                    }
                    for (CatalogueSource.Photo extra : source.photos(sku)) {
                        if (photos.stream().noneMatch(ph -> ph.fileName().equals(extra.fileName()))) photos.add(extra);
                    }
                } catch (Exception e) {
                    errors.add("Could not read photos: " + e.getMessage());
                }
                if (photos.isEmpty() && errors.isEmpty()) errors.add("No photo: fill the Image File column or add photos/" + sku + "/");
            }

            if (!errors.isEmpty()) {
                counts[1]++;
                String s = sku;
                errors.forEach(err -> issues.add(new SyncRun.Issue("ERROR", row.rowNumber(), s, err)));
                continue;
            }

            int offer = mrp.signum() == 0 ? 0 : mrp.subtract(price).multiply(BigDecimal.valueOf(100))
                    .divide(mrp, 0, RoundingMode.HALF_UP).intValue();
            String sheetOffer = row.any("%offer").replace("%", "").trim();
            if (!sheetOffer.isEmpty()) {
                try {
                    if (Math.abs(new BigDecimal(sheetOffer).intValue() - offer) > 1) {
                        issues.add(new SyncRun.Issue("WARN", row.rowNumber(), sku,
                                "%offer is " + sheetOffer + " but MRP/Selling_Price give " + offer + "%; showing " + offer + "%"));
                    }
                } catch (NumberFormatException ignored) {
                    // display-only column
                }
            }

            boolean isNew = !existing.containsKey(sku);
            Product p = existing.getOrDefault(sku, new Product(sku));
            p.setSource("EXCEL");
            p.setName(name);
            p.setCategory(cat);
            p.setDescription(cleanDescription(row.any("descriptions", "description"), row.any("details")));
            if (isNew) p.setCustomisationSpec(guessCustomisation(name, catName));
            p.setMrp(mrp);
            p.setSellingPrice(price);
            p.setOfferPct(offer);
            p.setStock(stock);
            p.setActive(true);
            p.setSyncedAt(now);
            p.setSlug(uniqueSlug(slugify(name) + "-" + sku.toLowerCase(), usedSlugs));
            syncImages(p, photos);
            products.save(p);
            counts[0]++;
        }

        existing.values().stream()
                .filter(p -> !"ADMIN".equals(p.getSource()))
                .filter(p -> !seen.contains(p.getSku()) || p.getSyncedAt() == null || p.getSyncedAt().isBefore(now))
                .forEach(p -> p.setActive(false));
    }

    private Map<String, Category> syncCategories(List<CatalogueSource.Row> rows, List<SyncRun.Issue> issues) {
        Map<String, Category> out = new HashMap<>();
        int order = 0;
        for (CatalogueSource.Row row : rows) {
            String name = row.get("category");
            if (name.isEmpty()) continue;
            Category c = categories.findByNameIgnoreCase(name).orElseGet(Category::new);
            c.setName(name);
            c.setSlug(slugify(name));
            c.setSortOrder(order++);
            c.setHsnCode(row.get("hsn_code"));
            try {
                if (!row.get("gst_rate").isEmpty()) c.setGstRate(new BigDecimal(row.get("gst_rate").replace("%", "")));
                if (!row.get("weight_g").isEmpty()) c.setWeightG(new BigDecimal(row.get("weight_g")).intValue());
                if (!row.get("length_cm").isEmpty()) c.setLengthCm(new BigDecimal(row.get("length_cm")));
                if (!row.get("width_cm").isEmpty()) c.setWidthCm(new BigDecimal(row.get("width_cm")));
                if (!row.get("height_cm").isEmpty()) c.setHeightCm(new BigDecimal(row.get("height_cm")));
                if (!row.get("lead_time_days").isEmpty()) c.setLeadTimeDays(new BigDecimal(row.get("lead_time_days")).intValue());
            } catch (NumberFormatException e) {
                issues.add(new SyncRun.Issue("WARN", row.rowNumber(), null, "Category '" + name + "' has a non-numeric value; defaults used"));
            }
            try {
                CustomisationSpec.parse(row.get("customisation"));
                c.setCustomisationSpec(row.get("customisation"));
            } catch (IllegalArgumentException e) {
                issues.add(new SyncRun.Issue("WARN", row.rowNumber(), null, "Category '" + name + "': " + e.getMessage()));
            }
            c.setColours(row.get("colours"));
            c.setSizes(row.get("sizes"));
            c.setReturnable(row.get("returnable").equalsIgnoreCase("yes") || row.get("returnable").equalsIgnoreCase("true"));
            out.put(name.toLowerCase(), categories.save(c));
        }
        return out;
    }

    /** Used when the workbook has no "categories" sheet: create the category with defaults. */
    private Category autoCategory(String name, Map<String, Category> cats) {
        Category c = categories.findByNameIgnoreCase(name).orElseGet(() -> {
            Category n = new Category();
            n.setName(name);
            n.setSlug(slugify(name));
            n.setSortOrder(cats.size());
            if (name.toLowerCase(Locale.ROOT).contains("key")) {
                // Name keychains: colour + lettering style choices, small parcel.
                n.setColours("Black, White, Red, Blue, Yellow, Pink, Purple, Green");
                n.setSizes("Classic, Script, Bold, Rounded");
                n.setWeightG(30);
                n.setLengthCm(new BigDecimal("10"));
                n.setWidthCm(new BigDecimal("6"));
                n.setHeightCm(new BigDecimal("2"));
                n.setLeadTimeDays(2);
            }
            return categories.save(n);
        });
        cats.put(name.toLowerCase(), c);
        return c;
    }

    private void syncImages(Product p, List<CatalogueSource.Photo> photos) {
        Map<String, ProductImage> current = p.getImages().stream()
                .collect(Collectors.toMap(ProductImage::getFileName, Function.identity()));
        List<ProductImage> next = new ArrayList<>();
        int i = 0;
        for (CatalogueSource.Photo photo : photos) {
            ProductImage img = current.remove(photo.fileName());
            String key = img != null ? img.getStorageKey() : null;
            if (img == null || !photo.checksum().equals(img.getChecksum()) || !storage.exists(key)) {
                // Web-sized copy; the original in the catalogue folder is left untouched.
                ImageOptimizer.Result small = ImageOptimizer.optimise(photo);
                String base = photo.fileName().contains(".") ? photo.fileName().substring(0, photo.fileName().lastIndexOf('.')) : photo.fileName();
                String newKey = "public/products/" + p.getSku() + "/" + (small != null ? base + small.extension() : photo.fileName());
                if (key != null && !key.equals(newKey)) storage.delete(key);
                key = newKey;
                if (small != null) storage.put(key, small.bytes(), small.contentType());
                else storage.put(key, photo.content(), photo.contentType());
            }
            if (img == null) img = new ProductImage(photo.fileName(), key, photo.checksum(), i);
            img.setStorageKey(key);
            img.setChecksum(photo.checksum());
            img.setSortOrder(i++);
            next.add(img);
        }
        current.values().forEach(gone -> storage.delete(gone.getStorageKey()));
        p.getImages().clear();
        p.getImages().addAll(next);
    }

    /** Used when the sheet has no stock column: products are printed to order. */
    public static final int MADE_TO_ORDER_STOCK = 999;

    /** "customisable" + "Keychain" → "Customisable Keychain"; "Hello Kitty" stays as is. */
    static String productName(String sub, String category) {
        String s = sub.trim();
        if (s.isEmpty()) return category.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.matches("custom(isable|ized|ised)?")) s = s + " " + category.trim();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Starting personalisation for new products from their name; the owner can change it in admin. */
    static String guessCustomisation(String name, String category) {
        String n = (name + " " + category).toLowerCase(Locale.ROOT);
        if (n.contains("figurine") && n.contains("custom")) return "photo:2-3, notes";
        if (n.contains("photo") || n.contains("stencil")) return "photo:1, notes";
        if (n.contains("key") && n.contains("custom")) return "style, colour, text";
        if (n.contains("name") || n.contains("alphabet") || n.contains("letter")) return "text";
        if (n.contains("custom")) return "text?, notes";
        return null;
    }

    /** Drops marketplace boilerplate ("Boost me", "thanks for supporting") and appends the Details column. */
    static String cleanDescription(String description, String details) {
        String text = description == null ? "" : description.replace("\r", "");
        StringBuilder out = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?✨🙏])\\s+|\\n+")) {
            String l = sentence.toLowerCase(Locale.ROOT);
            if (l.isBlank() || l.contains("boost") || l.contains("supporting me") || l.contains("makerworld")
                    || l.contains("thank you") || l.contains("thanks in advance") || l.contains("print profile")) continue;
            out.append(sentence.trim()).append(' ');
        }
        String result = out.toString().trim();
        if (details != null && !details.isBlank()) result = (result + "\n\n" + details.trim()).trim();
        return result.length() > 3900 ? result.substring(0, 3900) + "…" : result;
    }

    private static BigDecimal money(String raw, String col, List<String> errors) {
        String s = raw.replace("₹", "").replace(",", "").trim();
        if (s.isEmpty()) {
            errors.add(col + " is empty");
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            if (v.signum() <= 0) {
                errors.add(col + " must be greater than 0");
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            errors.add(col + " is not a number ('" + raw + "')");
            return null;
        }
    }

    private static String uniqueSlug(String base, Set<String> used) {
        String s = base;
        for (int n = 2; !used.add(s); n++) s = base + "-" + n;
        return s;
    }

    public static String slugify(String s) {
        String slug = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "item" : slug;
    }
}
