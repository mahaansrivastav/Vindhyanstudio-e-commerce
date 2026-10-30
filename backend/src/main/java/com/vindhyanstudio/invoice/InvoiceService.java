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
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPageEventHelper;
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

    private record Line(String description, String hsn, int qty, BigDecimal rate, BigDecimal gross, BigDecimal gstRate) {}

    private byte[] render(String no, LocalDate date, Order order, Customer customer) {
        boolean gst = seller.gstin() != null && !seller.gstin().isBlank();
        AddressSnapshot a = order.address();
        boolean intra = seller.state() != null && seller.state().equalsIgnoreCase(a.state());
        boolean hasLegal = seller.legalName() != null && !seller.legalName().isBlank();
        String legal = hasLegal ? seller.legalName() : seller.name();

        // Lines: products, then shipping billed as part of the goods (same HSN and rate as the main item).
        java.util.List<Line> lines = new java.util.ArrayList<>();
        OrderItem main = null;
        for (OrderItem i : order.getItems()) {
            if (main == null || i.lineTotal().compareTo(main.lineTotal()) > 0) main = i;
            lines.add(new Line(i.getName() + "\n" + i.getSku(), hsn(i.getHsnCode()), i.getQty(), i.getUnitPrice(), i.lineTotal(), i.getGstRate()));
        }
        if (order.getShippingFee().signum() > 0) {
            lines.add(new Line("Shipping & handling charges", main == null ? hsn(null) : hsn(main.getHsnCode()), 1,
                    order.getShippingFee(), order.getShippingFee(), main == null ? new BigDecimal("18") : main.getGstRate()));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 32, 32, 30, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new Watermark());
        doc.open();

        // ---- Header: logo | title + numbers ----
        PdfPTable top = new PdfPTable(new float[] {50, 50});
        top.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        Image logo = Watermark.logo();
        if (logo != null) {
            logo.scaleToFit(118, 98);
            logoCell.addElement(logo);
        } else {
            logoCell.addElement(new Phrase(seller.name(), H1));
        }
        top.addCell(logoCell);
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph(gst ? "TAX INVOICE" : "INVOICE", H1);
        title.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(title);
        Paragraph copy = new Paragraph("Original for Recipient", S);
        copy.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(copy);
        Paragraph ids = new Paragraph("\nInvoice No: " + no + "\nInvoice Date: " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                + "\nOrder No: " + order.getOrderNo()
                + "\nOrder Date: " + order.getCreatedAt().atZoneSameInstant(IST).format(DateTimeFormatter.ofPattern("dd MMM yyyy")), N);
        ids.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(ids);
        top.addCell(titleCell);
        doc.add(top);
        doc.add(new Paragraph(" ", S));

        // ---- Seller | Buyer ----
        PdfPTable parties = new PdfPTable(new float[] {50, 50});
        parties.setWidthPercentage(100);
        parties.addCell(party("Sold by",
                legal,
                (hasLegal ? "Brand: " + seller.name() + "\n" : "") + seller.address()
                        + "\nState: " + seller.state() + stateCodeSuffix(seller.state())
                        + (gst ? "\nGSTIN: " + seller.gstin() : "")
                        + "\nEmail: " + nz(seller.email())
                        + (seller.phone() == null || seller.phone().isBlank() ? "" : "\nPhone: " + seller.phone())));
        parties.addCell(party("Billed & shipped to",
                a.name(),
                a.oneLine() + "\nState: " + a.state() + stateCodeSuffix(a.state())
                        + "\nPhone: " + a.phone()
                        + (customer.getEmail() == null ? "" : "\nEmail: " + customer.getEmail())));
        doc.add(parties);

        // ---- Supply details ----
        PdfPTable meta = new PdfPTable(new float[] {34, 33, 33});
        meta.setWidthPercentage(100);
        meta.addCell(metaCell("Place of supply", a.state() + stateCodeSuffix(a.state())));
        meta.addCell(metaCell("Payment mode", order.getPaymentMode().name().equals("COD") ? "Cash on Delivery" : "Prepaid (online)"));
        meta.addCell(metaCell("Tax payable on reverse charge", "No"));
        doc.add(meta);
        doc.add(new Paragraph(" ", S));

        // ---- Items ----
        PdfPTable t;
        if (gst && intra) {
            t = new PdfPTable(new float[] {3.5f, 24, 7, 4.5f, 9.5f, 10.5f, 6.5f, 8.5f, 6.5f, 8.5f, 10.5f});
            for (String c : new String[] {"#", "Description", "HSN", "Qty", "Rate (incl.)", "Taxable value", "CGST\nRate", "CGST\nAmt", "SGST\nRate", "SGST\nAmt", "Total"}) t.addCell(headerCell(c));
        } else if (gst) {
            t = new PdfPTable(new float[] {3.5f, 30, 8, 5, 11, 12, 6.5f, 10, 12});
            for (String c : new String[] {"#", "Description", "HSN", "Qty", "Rate (incl.)", "Taxable value", "IGST\nRate", "IGST\nAmt", "Total"}) t.addCell(headerCell(c));
        } else {
            t = new PdfPTable(new float[] {4, 50, 8, 16, 16});
            for (String c : new String[] {"#", "Description", "Qty", "Rate", "Total"}) t.addCell(headerCell(c));
        }
        t.setWidthPercentage(100);
        t.setHeaderRows(1);

        BigDecimal taxableTotal = BigDecimal.ZERO, cgstTotal = BigDecimal.ZERO, sgstTotal = BigDecimal.ZERO, igstTotal = BigDecimal.ZERO;
        int n = 1;
        for (Line l : lines) {
            String num = l.description().startsWith("Shipping") ? "" : String.valueOf(n++);
            if (!gst) {
                addRow(t, num, l.description(), l.qty() + " Nos", money(l.rate()), money(l.gross()));
                continue;
            }
            BigDecimal taxable = backOut(l.gross(), l.gstRate());
            BigDecimal tax = l.gross().subtract(taxable);
            taxableTotal = taxableTotal.add(taxable);
            String rate = pct(l.gstRate());
            if (intra) {
                BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal other = tax.subtract(half);
                cgstTotal = cgstTotal.add(half);
                sgstTotal = sgstTotal.add(other);
                String halfRate = pct(l.gstRate().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
                addRow(t, num, l.description(), l.hsn(), String.valueOf(l.qty()), money(l.rate()), money(taxable),
                        halfRate, money(half), halfRate, money(other), money(l.gross()));
            } else {
                igstTotal = igstTotal.add(tax);
                addRow(t, num, l.description(), l.hsn(), String.valueOf(l.qty()), money(l.rate()), money(taxable), rate, money(tax), money(l.gross()));
            }
        }
        doc.add(t);
        Paragraph inr = new Paragraph("All amounts in INR", S);
        inr.setAlignment(Element.ALIGN_RIGHT);
        doc.add(inr);

        // ---- Totals + amount in words ----
        PdfPTable bottom = new PdfPTable(new float[] {58, 42});
        bottom.setWidthPercentage(100);
        bottom.setSpacingBefore(6);
        PdfPCell words = new PdfPCell();
        words.setBorder(Rectangle.NO_BORDER);
        words.setPaddingTop(4);
        words.addElement(new Phrase("Amount in words", S));
        words.addElement(new Phrase(amountInWords(order.getTotal()), B));
        if (order.getPaymentMode().name().equals("COD")) {
            words.addElement(new Phrase("\nAmount to be collected on delivery: Rs. " + order.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString(), N));
        }
        bottom.addCell(words);
        PdfPTable sum = new PdfPTable(new float[] {60, 40});
        if (gst) {
            sumRow(sum, "Total taxable value", money(taxableTotal));
            if (intra) {
                sumRow(sum, "CGST", money(cgstTotal));
                sumRow(sum, "SGST", money(sgstTotal));
            } else {
                sumRow(sum, "IGST", money(igstTotal));
            }
        } else {
            sumRow(sum, "Subtotal", money(order.getSubtotal()));
            if (order.getShippingFee().signum() > 0) sumRow(sum, "Shipping", money(order.getShippingFee()));
        }
        if (order.getDiscount().signum() > 0) sumRow(sum, "Discount", "- " + money(order.getDiscount()));
        PdfPCell gl = new PdfPCell(new Phrase("Grand total (INR)", B));
        gl.setBorder(Rectangle.TOP);
        gl.setPaddingTop(5);
        gl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell gv = new PdfPCell(new Phrase("Rs. " + order.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString(), B));
        gv.setBorder(Rectangle.TOP);
        gv.setPaddingTop(5);
        gv.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sum.addCell(gl);
        sum.addCell(gv);
        PdfPCell sumCell = new PdfPCell(sum);
        sumCell.setBorder(Rectangle.NO_BORDER);
        bottom.addCell(sumCell);
        doc.add(bottom);

        // ---- Notes ----
        Paragraph notes = new Paragraph((gst ? "All prices are inclusive of GST. " : "Seller is not registered under GST; no GST has been charged. ")
                + "Shipping charges are part of the supply of goods and taxed at the same rate."
                + "\nReturns & refunds as per our policy at the website. For help: " + nz(seller.email())
                + (seller.phone() == null || seller.phone().isBlank() ? "" : " / " + seller.phone()), S);
        notes.setSpacingBefore(14);
        doc.add(notes);
        Paragraph sys = new Paragraph("This is a system-generated invoice and does not require a signature.", B);
        sys.setSpacingBefore(16);
        sys.setAlignment(Element.ALIGN_CENTER);
        doc.add(sys);
        Paragraph thanks = new Paragraph("Thank you for shopping with " + seller.name() + "!", N);
        thanks.setAlignment(Element.ALIGN_CENTER);
        doc.add(thanks);
        doc.close();
        return out.toByteArray();
    }

    private String hsn(String categoryHsn) {
        if (categoryHsn != null && !categoryHsn.isBlank()) return categoryHsn.trim();
        return seller.defaultHsn() == null ? "" : seller.defaultHsn().trim();
    }

    private static PdfPCell party(String label, String name, String details) {
        PdfPCell c = new PdfPCell();
        c.setPadding(7);
        c.setBorderColor(Color.LIGHT_GRAY);
        c.addElement(new Phrase(label.toUpperCase(), S));
        c.addElement(new Phrase(name, B));
        c.addElement(new Phrase(details, N));
        return c;
    }

    private static PdfPCell metaCell(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setPadding(5);
        c.setBorderColor(Color.LIGHT_GRAY);
        c.addElement(new Phrase(label, S));
        c.addElement(new Phrase(value, B));
        return c;
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String pct(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString() + "%";
    }

    /** GST state codes (first two digits of a GSTIN). */
    private static final java.util.Map<String, String> STATE_CODES = java.util.Map.ofEntries(
            java.util.Map.entry("jammu and kashmir", "01"), java.util.Map.entry("himachal pradesh", "02"), java.util.Map.entry("punjab", "03"),
            java.util.Map.entry("chandigarh", "04"), java.util.Map.entry("uttarakhand", "05"), java.util.Map.entry("haryana", "06"),
            java.util.Map.entry("delhi", "07"), java.util.Map.entry("rajasthan", "08"), java.util.Map.entry("uttar pradesh", "09"),
            java.util.Map.entry("bihar", "10"), java.util.Map.entry("sikkim", "11"), java.util.Map.entry("arunachal pradesh", "12"),
            java.util.Map.entry("nagaland", "13"), java.util.Map.entry("manipur", "14"), java.util.Map.entry("mizoram", "15"),
            java.util.Map.entry("tripura", "16"), java.util.Map.entry("meghalaya", "17"), java.util.Map.entry("assam", "18"),
            java.util.Map.entry("west bengal", "19"), java.util.Map.entry("jharkhand", "20"), java.util.Map.entry("odisha", "21"),
            java.util.Map.entry("chhattisgarh", "22"), java.util.Map.entry("madhya pradesh", "23"), java.util.Map.entry("gujarat", "24"),
            java.util.Map.entry("dadra and nagar haveli and daman and diu", "26"), java.util.Map.entry("maharashtra", "27"),
            java.util.Map.entry("karnataka", "29"), java.util.Map.entry("goa", "30"), java.util.Map.entry("lakshadweep", "31"),
            java.util.Map.entry("kerala", "32"), java.util.Map.entry("tamil nadu", "33"), java.util.Map.entry("puducherry", "34"),
            java.util.Map.entry("andaman and nicobar islands", "35"), java.util.Map.entry("telangana", "36"),
            java.util.Map.entry("andhra pradesh", "37"), java.util.Map.entry("ladakh", "38"));

    static String stateCodeSuffix(String state) {
        String code = state == null ? null : STATE_CODES.get(state.trim().toLowerCase(java.util.Locale.ROOT));
        return code == null ? "" : " (" + code + ")";
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven",
            "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /** Indian numbering: 1,23,456.50 → "Rupees One Lakh Twenty-Three Thousand Four Hundred Fifty-Six and Fifty Paise Only". */
    static String amountInWords(BigDecimal amount) {
        BigDecimal v = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = v.longValue();
        int paise = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String words = rupees == 0 ? "Zero" : indian(rupees);
        return "Rupees " + words + (paise > 0 ? " and " + below100(paise) + " Paise" : "") + " Only";
    }

    private static String indian(long n) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000; n %= 10_000_000;
        long lakh = n / 100_000; n %= 100_000;
        long thousand = n / 1000; n %= 1000;
        long hundred = n / 100; n %= 100;
        if (crore > 0) sb.append(indian(crore)).append(" Crore ");
        if (lakh > 0) sb.append(below100((int) lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(below100((int) thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(ONES[(int) hundred]).append(" Hundred ");
        if (n > 0) sb.append(below100((int) n));
        return sb.toString().trim();
    }

    private static String below100(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 > 0 ? "-" + ONES[n % 10] : "");
    }

    /** Faint brand logo in the middle of every page. */
    private static final class Watermark extends PdfPageEventHelper {
        private static final byte[] LOGO = load();

        private static byte[] load() {
            try (var in = InvoiceService.class.getResourceAsStream("/invoice/logo.png")) {
                return in == null ? null : in.readAllBytes();
            } catch (Exception e) {
                return null;
            }
        }

        static Image logo() {
            try {
                return LOGO == null ? null : Image.getInstance(LOGO);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (LOGO == null) return;
            try {
                Image img = Image.getInstance(LOGO);
                img.scaleToFit(320, 320);
                Rectangle page = document.getPageSize();
                img.setAbsolutePosition((page.getWidth() - img.getScaledWidth()) / 2, (page.getHeight() - img.getScaledHeight()) / 2);
                PdfContentByte under = writer.getDirectContentUnder();
                under.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.05f);
                under.setGState(gs);
                under.addImage(img);
                under.restoreState();
            } catch (Exception ignored) {
                // watermark is decorative
            }
        }
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
