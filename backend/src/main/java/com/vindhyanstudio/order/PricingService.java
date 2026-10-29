package com.vindhyanstudio.order;

import java.math.BigDecimal;
import java.util.*;

import org.springframework.stereotype.Service;

import com.vindhyanstudio.catalogue.CustomisationSpec;
import com.vindhyanstudio.catalogue.Product;
import com.vindhyanstudio.catalogue.ProductRepository;
import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.storage.StorageService;

/** All prices and stock come from the database, never from the browser (BRD FR-11). */
@Service
public class PricingService {

    public static final int MAX_QTY_PER_LINE = 20;

    private final ProductRepository products;
    private final StorageService storage;
    private final AppProperties.Pricing cfg;

    public PricingService(ProductRepository products, StorageService storage, AppProperties props) {
        this.products = products;
        this.storage = storage;
        this.cfg = props.pricing();
    }

    public record LineRequest(String sku, int qty, Customisation customisation) {}

    public record QuoteLine(String sku, String name, String slug, String image, BigDecimal unitPrice, BigDecimal mrp,
                            int qty, BigDecimal lineTotal, int stock, String problem) {}

    public record Quote(List<QuoteLine> lines, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal total,
                        boolean codAvailable, boolean valid) {}

    public Quote quote(List<LineRequest> requests) {
        List<QuoteLine> lines = new ArrayList<>();
        Map<String, Integer> qtyBySku = new HashMap<>();
        requests.forEach(r -> qtyBySku.merge(r.sku(), Math.max(r.qty(), 0), Integer::sum));
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean valid = !requests.isEmpty();
        for (LineRequest r : requests) {
            Product p = products.findById(r.sku()).filter(Product::isActive).orElse(null);
            if (p == null) {
                lines.add(new QuoteLine(r.sku(), "Unavailable item", null, null, BigDecimal.ZERO, BigDecimal.ZERO, r.qty(),
                        BigDecimal.ZERO, 0, "This item is no longer available"));
                valid = false;
                continue;
            }
            String problem = null;
            if (r.qty() < 1 || r.qty() > MAX_QTY_PER_LINE) problem = "Quantity must be between 1 and " + MAX_QTY_PER_LINE;
            else if (p.getStock() <= 0) problem = "Out of stock";
            else if (qtyBySku.get(r.sku()) > p.getStock()) problem = "Only " + p.getStock() + " left";
            if (problem != null) valid = false;
            BigDecimal lineTotal = p.getSellingPrice().multiply(BigDecimal.valueOf(Math.max(r.qty(), 0)));
            subtotal = subtotal.add(lineTotal);
            String image = p.getImages().isEmpty() ? null : storage.publicUrl(p.getImages().get(0).getStorageKey());
            lines.add(new QuoteLine(p.getSku(), p.getName(), p.getSlug(), image, p.getSellingPrice(), p.getMrp(), r.qty(),
                    lineTotal, p.getStock(), problem));
        }
        BigDecimal shipping = shippingFee(subtotal);
        BigDecimal total = subtotal.add(shipping);
        boolean cod = cfg.codEnabled() && total.compareTo(cfg.codMaxOrderValue()) <= 0;
        return new Quote(lines, subtotal, shipping, total, cod, valid);
    }

    public BigDecimal shippingFee(BigDecimal subtotal) {
        if (subtotal.signum() == 0 || subtotal.compareTo(cfg.freeShippingAbove()) >= 0) return BigDecimal.ZERO;
        return cfg.shippingFlatFee();
    }

    /** Strict check used when placing an order: required inputs present and options valid. */
    public void validateCustomisation(Product p, Customisation c, long customerId) {
        CustomisationSpec spec = p.spec();
        Customisation in = c == null ? new Customisation(null, null, null, null, List.of()) : c;
        String label = p.getName() + ": ";
        if (spec.textRequired() && (in.text() == null || in.text().isBlank())) {
            throw ApiException.badRequest(label + "please enter the name/text to print");
        }
        if (in.text() != null && in.text().length() > 60) throw ApiException.badRequest(label + "text must be 60 characters or fewer");
        if (spec.colour() && !p.getCategory().colourList().isEmpty() && !p.getCategory().colourList().contains(in.colour())) {
            throw ApiException.badRequest(label + "please choose a colour");
        }
        if (spec.size() && !p.getCategory().sizeList().isEmpty() && !p.getCategory().sizeList().contains(in.size())) {
            throw ApiException.badRequest(label + "please choose a size");
        }
        if (in.notes() != null && in.notes().length() > 500) throw ApiException.badRequest(label + "notes are too long");
        List<String> photos = in.photos() == null ? List.of() : in.photos();
        if (photos.size() < spec.minPhotos() || photos.size() > spec.maxPhotos()) {
            String want = spec.minPhotos() == spec.maxPhotos() ? String.valueOf(spec.minPhotos()) : spec.minPhotos() + "–" + spec.maxPhotos();
            throw ApiException.badRequest(label + "please upload " + want + " photo" + (spec.maxPhotos() == 1 ? "" : "s"));
        }
        String prefix = "private/uploads/" + customerId + "/";
        for (String key : photos) {
            if (key == null || !key.startsWith(prefix) || key.contains("..") || !storage.exists(key)) {
                throw ApiException.badRequest(label + "an uploaded photo is missing, please upload it again");
            }
        }
    }
}
