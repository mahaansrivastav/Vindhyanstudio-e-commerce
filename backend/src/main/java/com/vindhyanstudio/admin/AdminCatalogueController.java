package com.vindhyanstudio.admin;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.vindhyanstudio.catalogue.*;
import com.vindhyanstudio.catalogue.sync.CatalogueSyncService;
import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.storage.StorageService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * Product and category management from the admin screen. Products created here have
 * source=ADMIN and are never touched by the Excel sync; Excel products can be "taken over".
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogueController {

    private static final Map<String, String> IMAGE_TYPES = Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StorageService storage;

    public AdminCatalogueController(ProductRepository products, CategoryRepository categories, StorageService storage) {
        this.products = products;
        this.categories = categories;
        this.storage = storage;
    }

    // ---------- DTOs ----------

    public record ImageDto(Long id, String url) {}

    public record ProductDto(String sku, String name, String slug, Long categoryId, String category, String description,
                             BigDecimal mrp, BigDecimal sellingPrice, int offerPct, int stock, boolean active, String source,
                             List<ImageDto> images) {}

    public record ProductForm(
            @Size(max = 50) @Pattern(regexp = "^$|[A-Za-z0-9_-]+", message = "may only contain letters, digits, - and _") String sku,
            @NotBlank @Size(max = 200) String name,
            @NotNull Long categoryId,
            @Size(max = 4000) String description,
            @NotNull @DecimalMin(value = "1", message = "must be at least 1") BigDecimal mrp,
            @NotNull @DecimalMin(value = "1", message = "must be at least 1") BigDecimal sellingPrice,
            @Min(0) @Max(100000) int stock,
            boolean active) {}

    public record CategoryDto(Long id, String name, String slug, String hsnCode, BigDecimal gstRate, int weightG,
                              BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, int leadTimeDays,
                              String customisation, String colours, String sizes, boolean returnable, long productCount) {}

    public record CategoryForm(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20) String hsnCode,
            @NotNull @DecimalMin("0") @DecimalMax("28") BigDecimal gstRate,
            @Min(1) @Max(50000) int weightG,
            @NotNull @DecimalMin("1") BigDecimal lengthCm,
            @NotNull @DecimalMin("1") BigDecimal widthCm,
            @NotNull @DecimalMin("1") BigDecimal heightCm,
            @Min(0) @Max(60) int leadTimeDays,
            @Size(max = 500) String customisation,
            @Size(max = 500) String colours,
            @Size(max = 500) String sizes,
            boolean returnable) {}

    // ---------- Products ----------

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductDto> list(@RequestParam(required = false) String q) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        return products.findAll().stream()
                .filter(p -> needle.isEmpty() || p.getName().toLowerCase().contains(needle) || p.getSku().toLowerCase().contains(needle))
                .sorted(Comparator.comparing((Product p) -> !p.isActive()).thenComparing(p -> p.getCategory().getSortOrder()).thenComparing(Product::getName))
                .map(this::dto).toList();
    }

    @GetMapping("/products/{sku}")
    @Transactional(readOnly = true)
    public ProductDto get(@PathVariable String sku) {
        return dto(find(sku));
    }

    @PostMapping("/products")
    @Transactional
    public ProductDto create(@Valid @RequestBody ProductForm f) {
        Category cat = category(f.categoryId());
        String sku = f.sku() == null || f.sku().isBlank() ? nextSku(cat) : f.sku().trim().toUpperCase();
        if (products.existsById(sku)) throw new ApiException(HttpStatus.CONFLICT, "SKU " + sku + " already exists");
        Product p = new Product(sku);
        p.setSource("ADMIN");
        apply(p, f, cat);
        return dto(products.save(p));
    }

    @PutMapping("/products/{sku}")
    @Transactional
    public ProductDto update(@PathVariable String sku, @Valid @RequestBody ProductForm f) {
        Product p = find(sku);
        requireAdminOwned(p);
        apply(p, f, category(f.categoryId()));
        return dto(p);
    }

    /** Moves an Excel product under admin control, so the sheet no longer overwrites it. */
    @PostMapping("/products/{sku}/take-over")
    @Transactional
    public ProductDto takeOver(@PathVariable String sku) {
        Product p = find(sku);
        p.setSource("ADMIN");
        return dto(p);
    }

    @PostMapping("/products/{sku}/images")
    @Transactional
    public ProductDto addImages(@PathVariable String sku, @RequestParam("files") List<MultipartFile> files) throws IOException {
        Product p = find(sku);
        requireAdminOwned(p);
        if (p.getImages().size() + files.size() > 10) throw ApiException.badRequest("A product can have at most 10 photos");
        int order = p.getImages().stream().mapToInt(ProductImage::getSortOrder).max().orElse(-1) + 1;
        for (MultipartFile file : files) {
            byte[] bytes = file.getBytes();
            String type = sniff(bytes);
            if (type == null) throw ApiException.badRequest(file.getOriginalFilename() + ": only JPG, PNG or WebP images are allowed");
            String name = UUID.randomUUID().toString().substring(0, 8) + IMAGE_TYPES.get(type);
            String key = "public/products/" + sku + "/" + name;
            storage.put(key, bytes, type);
            p.getImages().add(new ProductImage(name, key, null, order++));
        }
        products.flush(); // assign ids to the new images before returning them
        return dto(p);
    }

    @DeleteMapping("/products/{sku}/images/{imageId}")
    @Transactional
    public ProductDto deleteImage(@PathVariable String sku, @PathVariable Long imageId) {
        Product p = find(sku);
        requireAdminOwned(p);
        ProductImage img = p.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Photo not found"));
        p.getImages().remove(img);
        storage.delete(img.getStorageKey());
        return dto(p);
    }

    /** Body: image ids in the new order. The first one is the cover photo. */
    @PutMapping("/products/{sku}/images/order")
    @Transactional
    public ProductDto reorder(@PathVariable String sku, @RequestBody List<Long> ids) {
        Product p = find(sku);
        requireAdminOwned(p);
        for (ProductImage img : p.getImages()) {
            int idx = ids.indexOf(img.getId());
            img.setSortOrder(idx < 0 ? ids.size() + img.getSortOrder() : idx);
        }
        p.getImages().sort(Comparator.comparingInt(ProductImage::getSortOrder));
        return dto(p);
    }

    // ---------- Categories ----------

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        Map<Long, Long> counts = new HashMap<>();
        products.findAll().forEach(p -> counts.merge(p.getCategory().getId(), 1L, Long::sum));
        return categories.findAllByOrderBySortOrderAsc().stream().map(c -> categoryDto(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @PostMapping("/categories")
    @Transactional
    public CategoryDto createCategory(@Valid @RequestBody CategoryForm f) {
        if (categories.findByNameIgnoreCase(f.name().trim()).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "Category already exists");
        Category c = new Category();
        c.setSortOrder((int) categories.count());
        applyCategory(c, f);
        return categoryDto(categories.save(c), 0);
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryForm f) {
        Category c = category(id);
        categories.findByNameIgnoreCase(f.name().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new ApiException(HttpStatus.CONFLICT, "Another category has this name");
        });
        applyCategory(c, f);
        return categoryDto(c, 0);
    }

    // ---------- helpers ----------

    private void apply(Product p, ProductForm f, Category cat) {
        if (f.sellingPrice().compareTo(f.mrp()) > 0) throw ApiException.badRequest("Selling price can't be higher than MRP");
        p.setName(f.name().trim());
        p.setCategory(cat);
        p.setDescription(f.description() == null ? null : f.description().trim());
        p.setMrp(f.mrp().setScale(2, RoundingMode.HALF_UP));
        p.setSellingPrice(f.sellingPrice().setScale(2, RoundingMode.HALF_UP));
        p.setOfferPct(f.mrp().subtract(f.sellingPrice()).multiply(BigDecimal.valueOf(100)).divide(f.mrp(), 0, RoundingMode.HALF_UP).intValue());
        p.setStock(f.stock());
        p.setActive(f.active());
        p.setSlug(CatalogueSyncService.slugify(f.name()) + "-" + p.getSku().toLowerCase());
        p.setSyncedAt(OffsetDateTime.now());
    }

    private void applyCategory(Category c, CategoryForm f) {
        try {
            CustomisationSpec.parse(f.customisation());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Personalisation: " + e.getMessage());
        }
        c.setName(f.name().trim());
        c.setSlug(CatalogueSyncService.slugify(f.name()));
        c.setHsnCode(blankToNull(f.hsnCode()));
        c.setGstRate(f.gstRate());
        c.setWeightG(f.weightG());
        c.setLengthCm(f.lengthCm());
        c.setWidthCm(f.widthCm());
        c.setHeightCm(f.heightCm());
        c.setLeadTimeDays(f.leadTimeDays());
        c.setCustomisationSpec(blankToNull(f.customisation()));
        c.setColours(blankToNull(f.colours()));
        c.setSizes(blankToNull(f.sizes()));
        c.setReturnable(f.returnable());
    }

    private String nextSku(Category cat) {
        String letters = cat.getName().replaceAll("[^A-Za-z ]", "").trim();
        String[] words = letters.split("\\s+");
        String prefix = (words.length > 1 ? "" + words[0].charAt(0) + words[1].charAt(0) : letters.substring(0, Math.min(2, letters.length()))).toUpperCase();
        if (prefix.length() < 2) prefix = "VS";
        for (int n = 1; ; n++) {
            String sku = String.format("%s-%03d", prefix, n);
            if (!products.existsById(sku)) return sku;
        }
    }

    private void requireAdminOwned(Product p) {
        if (!"ADMIN".equals(p.getSource())) {
            throw new ApiException(HttpStatus.CONFLICT, "This product comes from the Excel sheet. Click \"Manage here instead\" first, or edit it in the sheet.");
        }
    }

    private Product find(String sku) {
        return products.findById(sku).orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.badRequest("Choose a category"));
    }

    private ProductDto dto(Product p) {
        return new ProductDto(p.getSku(), p.getName(), p.getSlug(), p.getCategory().getId(), p.getCategory().getName(),
                p.getDescription(), p.getMrp(), p.getSellingPrice(), p.getOfferPct(), p.getStock(), p.isActive(), p.getSource(),
                p.getImages().stream().map(i -> new ImageDto(i.getId(), storage.publicUrl(i.getStorageKey()))).toList());
    }

    private static CategoryDto categoryDto(Category c, long count) {
        return new CategoryDto(c.getId(), c.getName(), c.getSlug(), c.getHsnCode(), c.getGstRate(), c.getWeightG(), c.getLengthCm(),
                c.getWidthCm(), c.getHeightCm(), c.getLeadTimeDays(), c.getCustomisationSpec(), c.getColours(), c.getSizes(),
                c.isReturnable(), count);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
