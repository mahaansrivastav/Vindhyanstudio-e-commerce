package com.vindhyanstudio.catalogue;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fileName;
    private String storageKey;
    private String checksum;
    private int sortOrder;

    protected ProductImage() {}

    public ProductImage(String fileName, String storageKey, String checksum, int sortOrder) {
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.checksum = checksum;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
