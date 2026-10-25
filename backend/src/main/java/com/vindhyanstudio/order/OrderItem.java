package com.vindhyanstudio.order;

import java.math.BigDecimal;

import com.vindhyanstudio.common.Json;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sku;
    private String name;
    private BigDecimal unitPrice;
    private BigDecimal mrp;
    private int qty;
    private String hsnCode;
    private BigDecimal gstRate;
    private String customisationJson;

    protected OrderItem() {}

    public OrderItem(String sku, String name, BigDecimal unitPrice, BigDecimal mrp, int qty, String hsnCode,
                     BigDecimal gstRate, Customisation customisation) {
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
        this.mrp = mrp;
        this.qty = qty;
        this.hsnCode = hsnCode;
        this.gstRate = gstRate;
        this.customisationJson = customisation == null || customisation.isEmpty() ? null : Json.write(customisation);
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(qty));
    }

    public Customisation customisation() {
        return Json.read(customisationJson, Customisation.class);
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getMrp() { return mrp; }
    public int getQty() { return qty; }
    public String getHsnCode() { return hsnCode; }
    public BigDecimal getGstRate() { return gstRate; }
}
