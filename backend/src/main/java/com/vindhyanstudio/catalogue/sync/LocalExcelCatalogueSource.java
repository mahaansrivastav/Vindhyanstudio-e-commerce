package com.vindhyanstudio.catalogue.sync;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.vindhyanstudio.config.AppProperties;

/**
 * Reads {@code <local-path>/catalogue.xlsx} (sheets "products" and optional "categories")
 * and photos from {@code <local-path>/photos/<SKU_id>/}.
 */
@Component
@ConditionalOnProperty(name = "app.catalogue.source", havingValue = "local", matchIfMissing = true)
public class LocalExcelCatalogueSource implements CatalogueSource {

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp", "gif", "image/gif", "svg", "image/svg+xml");

    private final Path root;

    public LocalExcelCatalogueSource(AppProperties props) {
        this.root = Path.of(props.catalogue().localPath()).toAbsolutePath().normalize();
    }

    private Path workbookPath() {
        return root.resolve("catalogue.xlsx");
    }

    @Override
    public Snapshot read() throws Exception {
        Path file = workbookPath();
        if (!Files.exists(file)) throw new IllegalStateException("Catalogue file not found: " + file);
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            Sheet products = Optional.ofNullable(wb.getSheet("products")).orElse(wb.getSheetAt(0));
            Sheet categories = wb.getSheet("categories");
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            return new Snapshot(rows(products, ev), categories == null ? List.of() : rows(categories, ev));
        }
    }

    private static List<Row> rows(Sheet sheet, FormulaEvaluator ev) {
        DataFormatter fmt = new DataFormatter();
        Iterator<org.apache.poi.ss.usermodel.Row> it = sheet.rowIterator();
        if (!it.hasNext()) return List.of();
        List<String> headers = new ArrayList<>();
        for (Cell c : it.next()) {
            while (headers.size() < c.getColumnIndex()) headers.add("");
            headers.add(fmt.formatCellValue(c).trim().toLowerCase());
        }
        List<Row> out = new ArrayList<>();
        while (it.hasNext()) {
            org.apache.poi.ss.usermodel.Row r = it.next();
            Map<String, String> cells = new HashMap<>();
            boolean any = false;
            for (int i = 0; i < headers.size(); i++) {
                String v;
                try {
                    v = fmt.formatCellValue(r.getCell(i), ev).trim();
                } catch (RuntimeException e) {
                    v = fmt.formatCellValue(r.getCell(i)).trim(); // formula the evaluator can't handle
                }
                if (!v.isEmpty()) any = true;
                cells.put(headers.get(i), v);
            }
            if (any) out.add(new Row(r.getRowNum() + 1, cells));
        }
        return out;
    }

    @Override
    public Photo file(String relativePath) throws Exception {
        Path f = root.resolve(relativePath.trim()).normalize();
        if (!f.startsWith(root) || !Files.isRegularFile(f)) return null;
        String name = f.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!IMAGE_TYPES.containsKey(ext)) return null;
        byte[] bytes = Files.readAllBytes(f);
        return new Photo(name, sha256(bytes), bytes, IMAGE_TYPES.get(ext));
    }

    @Override
    public List<Photo> photos(String sku) throws Exception {
        Path dir = root.resolve("photos").resolve(sku).normalize();
        if (!dir.startsWith(root) || !Files.isDirectory(dir)) return List.of();
        List<Photo> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                String name = f.getFileName().toString();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
                if (!Files.isRegularFile(f) || !IMAGE_TYPES.containsKey(ext) || name.startsWith(".")) continue;
                byte[] bytes = Files.readAllBytes(f);
                out.add(new Photo(name, sha256(bytes), bytes, IMAGE_TYPES.get(ext)));
            }
        }
        return out;
    }

    @Override
    public synchronized void writeStock(Map<String, Integer> stockBySku) throws Exception {
        Path file = workbookPath();
        Path tmp = file.resolveSibling(".catalogue.xlsx.tmp");
        try (InputStream in = Files.newInputStream(file); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = Optional.ofNullable(wb.getSheet("products")).orElse(wb.getSheetAt(0));
            DataFormatter fmt = new DataFormatter();
            org.apache.poi.ss.usermodel.Row header = sheet.getRow(sheet.getFirstRowNum());
            int skuCol = -1, stockCol = -1;
            for (Cell c : header) {
                String h = fmt.formatCellValue(c).trim().toLowerCase();
                if (h.equals("sku_id")) skuCol = c.getColumnIndex();
                if (h.equals("stock")) stockCol = c.getColumnIndex();
            }
            if (skuCol < 0 || stockCol < 0) return;
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
                if (r == null) continue;
                Integer stock = stockBySku.get(fmt.formatCellValue(r.getCell(skuCol)).trim());
                if (stock == null) continue;
                Cell cell = r.getCell(stockCol);
                if (cell == null) cell = r.createCell(stockCol);
                cell.setCellValue(stock);
            }
            try (OutputStream out = Files.newOutputStream(tmp)) {
                wb.write(out);
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
