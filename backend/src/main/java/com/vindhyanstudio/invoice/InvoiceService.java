package com.vindhyanstudio.invoice;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.customer.Customer;
import com.vindhyanstudio.customer.CustomerRepository;
import com.vindhyanstudio.order.AddressSnapshot;
import com.vindhyanstudio.order.Order;
import com.vindhyanstudio.order.OrderItem;
import com.vindhyanstudio.order.OrderRepository;
import com.vindhyanstudio.storage.StorageService;

/**
 * Generates the invoice PDF after an order is confirmed (BRD FR-18, FR-19).
 * Prices are GST-inclusive; the taxable value is backed out per line. Intra-state → CGST+SGST,
 * inter-state → IGST. Without a seller GSTIN a plain invoice (no tax split) is issued.
 * Get the HSN codes and rates confirmed by the owner's CA before going live.
 */
@Service
public class InvoiceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font B = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font N = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font S = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);

    private final InvoiceRepository invoices;
    private final InvoiceCounterRepository counters;
    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final StorageService storage;
    private final AppProperties.Seller seller;

    public InvoiceService(InvoiceRepository invoices, InvoiceCounterRepository counters, OrderRepository orders,
                          CustomerRepository customers, StorageService storage, AppProperties props) {
        this.invoices = invoices;
        this.counters = counters;
        this.orders = orders;
        this.customers = customers;
        this.storage = storage;
        this.seller = props.seller();
    }

    public Optional<Invoice> forOrder(Long orderId) {
        return invoices.findByOrderIdAndType(orderId, Invoice.Type.INVOICE);
    }

    public byte[] pdf(Invoice invoice) {
        return storage.get(invoice.getStorageKey());
    }

    @Transactional
    public Invoice generate(Long orderId) {
        Optional<Invoice> existing = forOrder(orderId);
        if (existing.isPresent()) return existing.get();
        Order order = orders.findById(orderId).orElseThrow();
        Customer customer = customers.findById(order.getCustomerId()).orElseThrow();
        LocalDate date = LocalDate.now(IST);
        String no = nextNumber("VS", date);
        String key = "private/invoices/" + no.replace('/', '-') + ".pdf";
        storage.put(key, render(no, date, order, customer), "application/pdf");
        return invoices.save(new Invoice(orderId, no, Invoice.Type.INVOICE, key));
    }

    /** Gapless per financial year (April–March), e.g. VS/2026-27/00001. */
    private String nextNumber(String prefix, LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        String fy = start + "-" + String.format("%02d", (start + 1) % 100);
        String key = prefix + ":" + fy;
        InvoiceCounter c = counters.lock(key).orElseGet(() -> counters.saveAndFlush(new InvoiceCounter(key)));
        c.lastNo++;
        return prefix + "/" + fy + "/" + String.format("%05d", c.lastNo);
    }

    private byte[] render(String no, LocalDate date, Order order, Customer customer) {
        boolean gst = seller.gstin() != null && !seller.gstin().isBlank();
        AddressSnapshot a = order.address();
        boolean intra = seller.state() != null && seller.state().equalsIgnoreCase(a.state());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph(gst ? "TAX INVOICE" : "INVOICE", H1));
        doc.add(new Paragraph(" ", S));

        PdfPTable head = new PdfPTable(2);
        head.setWidthPercentage(100);
        head.addCell(box(seller.name() + "\n" + seller.address()
                + (gst ? "\nGSTIN: " + seller.gstin() : "") + (seller.email() == null ? "" : "\n" + seller.email())));
        head.addCell(box("Invoice No: " + no + "\nInvoice Date: " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                + "\nOrder No: " + order.getOrderNo() + "\nPayment: " + (order.getPaymentMode().name().equals("COD") ? "Cash on Delivery" : "Prepaid (online)")
                + "\nPlace of supply: " + a.state()));
        head.addCell(box("Bill / Ship to:\n" + a.name() + "\n" + a.oneLine() + "\nPhone: " + a.phone()
                + (customer.getEmail() == null ? "" : "\n" + customer.getEmail())));
        head.addCell(box(""));
        doc.add(head);
        doc.add(new Paragraph(" ", S));

        String[] cols = gst
                ? new String[] {"#", "Item", "HSN", "Qty", "Rate (incl.)", "Taxable", intra ? "CGST+SGST" : "IGST", "Amount"}
                : new String[] {"#", "Item", "Qty", "Rate", "Amount"};
        PdfPTable t = new PdfPTable(gst ? new float[] {3, 28, 9, 5, 12, 12, 14, 12} : new float[] {4, 50, 8, 16, 16});
        t.setWidthPercentage(100);
        for (String c : cols) t.addCell(headerCell(c));

        BigDecimal taxableTotal = BigDecimal.ZERO, taxTotal = BigDecimal.ZERO, maxRate = BigDecimal.ZERO;
        int n = 1;
        for (OrderItem i : order.getItems()) {
            BigDecimal gross = i.lineTotal();
            maxRate = maxRate.max(i.getGstRate());
            if (gst) {
                BigDecimal taxable = backOut(gross, i.getGstRate());
                BigDecimal tax = gross.subtract(taxable);
                taxableTotal = taxableTotal.add(taxable);
                taxTotal = taxTotal.add(tax);
                addRow(t, String.valueOf(n++), i.getName() + " (" + i.getSku() + ")", nz(i.getHsnCode()), String.valueOf(i.getQty()),
                        rs(i.getUnitPrice()), rs(taxable), rs(tax) + " @" + i.getGstRate().stripTrailingZeros().toPlainString() + "%", rs(gross));
            } else {
                addRow(t, String.valueOf(n++), i.getName() + " (" + i.getSku() + ")", String.valueOf(i.getQty()), rs(i.getUnitPrice()), rs(gross));
            }
        }
        if (order.getShippingFee().signum() > 0) {
            if (gst) {
                BigDecimal taxable = backOut(order.getShippingFee(), maxRate);
                BigDecimal tax = order.getShippingFee().subtract(taxable);
                taxableTotal = taxableTotal.add(taxable);
                taxTotal = taxTotal.add(tax);
                addRow(t, "", "Shipping & handling", "9965", "1", rs(order.getShippingFee()), rs(taxable),
                        rs(tax) + " @" + maxRate.stripTrailingZeros().toPlainString() + "%", rs(order.getShippingFee()));
            } else {
                addRow(t, "", "Shipping & handling", "1", rs(order.getShippingFee()), rs(order.getShippingFee()));
            }
        }
        doc.add(t);
        doc.add(new Paragraph(" ", S));

        PdfPTable sum = new PdfPTable(new float[] {70, 30});
        sum.setWidthPercentage(100);
        if (gst) {
            if (intra) {
                BigDecimal half = taxTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                sumRow(sum, "Taxable value", rs(taxableTotal));
                sumRow(sum, "CGST", rs(half));
                sumRow(sum, "SGST", rs(taxTotal.subtract(half)));
            } else {
                sumRow(sum, "Taxable value", rs(taxableTotal));
                sumRow(sum, "IGST", rs(taxTotal));
            }
        }
        if (order.getDiscount().signum() > 0) sumRow(sum, "Discount", "- " + rs(order.getDiscount()));
        PdfPCell l = new PdfPCell(new Phrase("Total", B));
        l.setBorder(Rectangle.TOP);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell r = new PdfPCell(new Phrase(rs(order.getTotal()), B));
        r.setBorder(Rectangle.TOP);
        r.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sum.addCell(l);
        sum.addCell(r);
        doc.add(sum);

        doc.add(new Paragraph(" ", S));
        doc.add(new Paragraph(gst ? "All prices are inclusive of GST. This is a computer-generated invoice and needs no signature."
                : "Seller is not registered under GST; no GST has been charged. This is a computer-generated invoice.", S));
        doc.close();
        return out.toByteArray();
    }

    private static BigDecimal backOut(BigDecimal gross, BigDecimal rate) {
        return gross.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(100).add(rate), 2, RoundingMode.HALF_UP);
    }

    private static String rs(BigDecimal v) {
        return "Rs. " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static PdfPCell box(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, N));
        c.setPadding(6);
        c.setBorderColor(Color.LIGHT_GRAY);
        return c;
    }

    private static PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, B));
        c.setBackgroundColor(new Color(240, 240, 240));
        c.setPadding(4);
        return c;
    }

    private static void addRow(PdfPTable t, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i], N));
            c.setPadding(4);
            if (i >= 2) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(c);
        }
    }

    private static void sumRow(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, N));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell r = new PdfPCell(new Phrase(value, N));
        r.setBorder(Rectangle.NO_BORDER);
        r.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(r);
    }
}
