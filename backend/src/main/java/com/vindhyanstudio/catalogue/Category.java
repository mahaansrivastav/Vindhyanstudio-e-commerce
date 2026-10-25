package com.vindhyanstudio.catalogue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String slug;
    private String hsnCode;
    private BigDecimal gstRate = new BigDecimal("18");
    @Column(name = "weight_g")
    private int weightG = 100;
    private BigDecimal lengthCm = BigDecimal.TEN;
    private BigDecimal widthCm = BigDecimal.TEN;
    private BigDecimal heightCm = new BigDecimal("5");
    private int leadTimeDays = 3;
    private String customisationSpec;
    private String colours;
    private String sizes;
    private boolean returnable;
    private int sortOrder;

    public CustomisationSpec spec() {
        return CustomisationSpec.parse(customisationSpec);
    }

    public List<String> colourList() {
        return csv(colours);
    }

    public List<String> sizeList() {
        return csv(sizes);
    }

    private static List<String> csv(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public BigDecimal getGstRate() { return gstRate; }
    public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }
    public int getWeightG() { return weightG; }
    public void setWeightG(int weightG) { this.weightG = weightG; }
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
    public BigDecimal getWidthCm() { return widthCm; }
    public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public String getCustomisationSpec() { return customisationSpec; }
    public void setCustomisationSpec(String customisationSpec) { this.customisationSpec = customisationSpec; }
    public String getColours() { return colours; }
    public void setColours(String colours) { this.colours = colours; }
    public String getSizes() { return sizes; }
    public void setSizes(String sizes) { this.sizes = sizes; }
    public boolean isReturnable() { return returnable; }
    public void setReturnable(boolean returnable) { this.returnable = returnable; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
