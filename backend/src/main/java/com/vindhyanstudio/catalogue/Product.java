package com.vindhyanstudio.catalogue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private String sku;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    private String name;
    private String slug;
    private String description;
    private BigDecimal mrp;
    private BigDecimal sellingPrice;
    private int offerPct;
    private int stock;
    private boolean active = true;
    /** EXCEL = owned by the catalogue sheet; ADMIN = created/edited in the admin screen. */
    private String source = "EXCEL";
    /** Personalisation for this product (e.g. "style, colour, text"); null = use the category's. */
    private String customisationSpec;
    private OffsetDateTime syncedAt;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sku", nullable = false)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {}

    public Product(String sku) {
        this.sku = sku;
    }

    public String getSku() { return sku; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    public int getOfferPct() { return offerPct; }
    public void setOfferPct(int offerPct) { this.offerPct = offerPct; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    /** Effective personalisation: the product's own setting, else its category's. */
    public CustomisationSpec spec() {
        return customisationSpec != null ? CustomisationSpec.parse(customisationSpec) : category.spec();
    }

    public String getCustomisationSpec() { return customisationSpec; }
    public void setCustomisationSpec(String customisationSpec) { this.customisationSpec = customisationSpec; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(OffsetDateTime syncedAt) { this.syncedAt = syncedAt; }
    public List<ProductImage> getImages() { return images; }
}
