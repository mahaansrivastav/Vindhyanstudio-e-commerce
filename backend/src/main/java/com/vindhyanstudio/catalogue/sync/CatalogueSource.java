/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.catalogue.sync;

import java.util.List;
import java.util.Map;

/**
 * Where the owner's catalogue lives. v1: {@link LocalExcelCatalogueSource} (an .xlsx file and a
 * photos/ folder on disk). Planned: GoogleDriveCatalogueSource (Google Sheet + Drive folder),
 * selected with app.catalogue.source.
 */
public interface CatalogueSource {

    /** One spreadsheet row: header name (lower-cased) → cell text, plus the 1-based row number. */
    record Row(int rowNumber, Map<String, String> cells) {
        public String get(String header) {
            String v = cells.get(header.toLowerCase());
            return v == null ? "" : v.trim();
        }

        /** First column whose header equals, or else starts with, one of the keys (case-insensitive). */
        public String any(String... keys) {
            String h = header(keys);
            return h == null ? "" : get(h);
        }

        public boolean has(String... keys) {
            return header(keys) != null;
        }

        private String header(String... keys) {
            for (String k : keys) if (cells.containsKey(k.toLowerCase())) return k.toLowerCase();
            for (String k : keys) {
                for (String h : cells.keySet()) if (h.startsWith(k.toLowerCase())) return h;
            }
            return null;
        }
    }

    record Photo(String fileName, String checksum, byte[] content, String contentType) {}

    record Snapshot(List<Row> products, List<Row> categories) {}

    Snapshot read() throws Exception;

    /** One image by path relative to the catalogue folder (the sheet's "Image File" column), or null if missing. */
    Photo file(String relativePath) throws Exception;

    /** All images in photos/&lt;sku&gt;/, sorted by file name. Empty if the folder is missing. */
    List<Photo> photos(String sku) throws Exception;

    /** Writes current stock figures back to the sheet so the owner always sees real stock (BRD FR-28). */
    void writeStock(Map<String, Integer> stockBySku) throws Exception;
}
