package com.vindhyanstudio.admin;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.vindhyanstudio.catalogue.sync.SyncRunRepository;
import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.common.CurrentUser;
import com.vindhyanstudio.customer.CustomerRepository;

/** Dashboard numbers, Excel reports and the Excel product upload for the owner. ADMIN role only. */
@RestController
@RequestMapping("/api/admin")
public class AdminBusinessController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BusinessReportService reports;
    private final ProductImportService imports;
    private final SyncRunRepository runs;
    private final CustomerRepository customers;

    public AdminBusinessController(BusinessReportService reports, ProductImportService imports, SyncRunRepository runs, CustomerRepository customers) {
        this.reports = reports;
        this.imports = imports;
        this.runs = runs;
        this.customers = customers;
    }

    @GetMapping("/dashboard")
    BusinessReportService.Dashboard dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.dashboard(from, to);
    }

    @GetMapping("/reports/sales")
    ResponseEntity<byte[]> salesReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        return xlsx(reports.report(from, to), "vindhyan-report-" + from + "-to-" + to + ".xlsx");
    }

    @GetMapping("/products/export")
    ResponseEntity<byte[]> exportProducts() throws IOException {
        return xlsx(imports.exportProducts(), "vindhyan-products-" + LocalDate.now(BusinessReportService.IST) + ".xlsx");
    }

    @PostMapping("/products/import/preview")
    ProductImportService.Preview preview(@RequestParam("file") MultipartFile file) throws IOException {
        return imports.preview(read(file));
    }

    /** {@code skus}: comma-separated SKUs the owner ticked on the review screen. */
    @PostMapping("/products/import/apply")
    ProductImportService.Applied apply(@RequestParam("file") MultipartFile file, @RequestParam("skus") String skus) throws IOException {
        Set<String> selected = Arrays.stream(skus.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (selected.isEmpty()) throw ApiException.badRequest("Tick at least one product to update");
        String by = customers.findById(CurrentUser.id()).map(c -> c.getEmail() != null ? c.getEmail() : c.getPhone()).orElse("admin");
        return imports.apply(read(file), selected, by);
    }

    @GetMapping("/products/import/history")
    List<AdminController.SyncRunDto> history() {
        return runs.findTop10ByOrderByIdDesc().stream().map(AdminController.SyncRunDto::of).toList();
    }

    private static byte[] read(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw ApiException.badRequest("The file is empty");
        String name = String.valueOf(file.getOriginalFilename()).toLowerCase();
        if (!name.endsWith(".xlsx")) throw ApiException.badRequest("Please upload an Excel file (.xlsx). In Excel, use File → Save As → Excel Workbook.");
        return file.getBytes();
    }

    private static ResponseEntity<byte[]> xlsx(byte[] body, String name) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }
}
