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
    }

    record Photo(String fileName, String checksum, byte[] content, String contentType) {}

    record Snapshot(List<Row> products, List<Row> categories) {}

    Snapshot read() throws Exception;

    /** All images in photos/&lt;sku&gt;/, sorted by file name. Empty if the folder is missing. */
    List<Photo> photos(String sku) throws Exception;

    /** Writes current stock figures back to the sheet so the owner always sees real stock (BRD FR-28). */
    void writeStock(Map<String, Integer> stockBySku) throws Exception;
}
