/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.order.OrderView;
import com.vindhyanstudio.order.PaymentMode;

/**
 * Branded HTML emails (same look as the website: white card, charcoal buttons, Inter-style type)
 * with a plain-text version of each for clients that don't show HTML.
 * Built with tables and inline styles, which is what email clients reliably support.
 */
@Component
public class EmailTemplates {

    public record Email(String subject, String text, String html) {}

    private static final String INK = "#0a0a0a", MUTED = "#6b7280", LINE = "#e5e7eb", SURFACE = "#f5f5f7", ACCENT = "#b4741f", OK = "#15803d";
    private static final String FONT = "Inter,-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private final AppProperties props;

    public EmailTemplates(AppProperties props) {
        this.props = props;
    }

    // ---------- emails ----------

    public Email otp(String code, int minutes) {
        String subject = code + " is your " + brand() + " login code";
        String text = code + " is your " + brand() + " login code. It expires in " + minutes + " minutes.\n\n"
                + "If you didn't try to sign in, you can ignore this email.";
        String body = h1("Your login code")
                + p("Enter this code on the website to sign in. It expires in " + minutes + " minutes.")
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0;\"><tr><td style=\"background:" + SURFACE
                + ";border-radius:12px;padding:18px 28px;font-family:" + FONT + ";font-size:34px;font-weight:700;letter-spacing:10px;color:" + INK + ";\">"
                + esc(code) + "</td></tr></table>"
                + small("Never share this code with anyone. " + brand() + " will never ask for it on a call or WhatsApp. "
                + "If you didn't try to sign in, you can ignore this email.");
        return new Email(subject, text, page(code + " is your login code", body));
    }

    public Email orderConfirmed(OrderView o) {
        boolean cod = o.paymentMode() == PaymentMode.COD;
        String subject = "Order " + o.orderNo() + " confirmed";
        String text = "Hi " + first(o) + ",\n\nThank you for your order " + o.orderNo() + ".\n\n"
                + o.items().stream().map(i -> "• " + i.name() + " × " + i.qty() + "  " + rs(i.lineTotal())).collect(Collectors.joining("\n"))
                + "\n\nTotal: " + rs(o.total()) + (cod ? " (pay on delivery)" : " (paid)")
                + "\n\nTrack your order and download the invoice: " + orderLink(o) + "\n\n— " + brand();
        String body = badge("Order confirmed", OK)
                + h1("Thank you, " + esc(first(o)) + "!")
                + p("We've received your order <b style=\"color:" + INK + ";\">" + esc(o.orderNo()) + "</b> and it's now in our studio queue. "
                + "We'll email you again when it ships.")
                + steps(1)
                + items(o)
                + totals(o, cod)
                + address(o)
                + button("Track your order", orderLink(o))
                + small("You can download your GST invoice from the order page.");
        return new Email(subject, text, page("Order " + o.orderNo() + " confirmed. We'll let you know when it ships.", body));
    }

    public Email shipped(OrderView o) {
        String track = o.shipment() != null && o.shipment().trackingUrl() != null ? o.shipment().trackingUrl() : orderLink(o);
        String courier = o.shipment() != null && o.shipment().courier() != null ? o.shipment().courier() : null;
        String awb = o.shipment() != null ? o.shipment().awb() : null;
        String subject = "Your order " + o.orderNo() + " is on its way";
        String text = "Hi " + first(o) + ",\n\nGood news: your order " + o.orderNo() + " has shipped"
                + (courier != null ? " with " + courier : "") + (awb != null ? " (tracking no. " + awb + ")" : "") + ".\n\n"
                + "Track it here: " + track + "\n\n— " + brand();
        String shipBox = courier == null && awb == null ? "" : "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:20px 0 4px;background:"
                + SURFACE + ";border-radius:12px;\"><tr><td style=\"padding:16px 20px;font-family:" + FONT + ";font-size:14px;color:" + INK + ";\">"
                + (courier != null ? "<div style=\"color:" + MUTED + ";font-size:12px;\">Courier</div><div style=\"font-weight:600;margin-bottom:10px;\">" + esc(courier) + "</div>" : "")
                + (awb != null ? "<div style=\"color:" + MUTED + ";font-size:12px;\">Tracking number</div><div style=\"font-weight:600;letter-spacing:.5px;\">" + esc(awb) + "</div>" : "")
                + "</td></tr></table>";
        String body = badge("Shipped", INK)
                + h1("Your order is on its way")
                + p("Hi " + esc(first(o)) + ", your order <b style=\"color:" + INK + ";\">" + esc(o.orderNo()) + "</b> has left our studio"
                + (courier != null ? " with " + esc(courier) : "") + ". Most orders arrive in 2–7 working days.")
                + steps(2)
                + shipBox
                + button("Track your shipment", track)
                + items(o)
                + address(o)
                + (o.paymentMode() == PaymentMode.COD ? small("Please keep " + rs(o.total()) + " ready to pay on delivery.") : "");
        return new Email(subject, text, page("Order " + o.orderNo() + " has shipped. Track it here.", body));
    }

    public Email delivered(OrderView o) {
        String subject = "Your order " + o.orderNo() + " has been delivered";
        String text = "Hi " + first(o) + ",\n\nYour order " + o.orderNo() + " has been delivered. We hope you love it!\n\n"
                + "Anything not right? Reply to this email or message us on WhatsApp within 48 hours.\n\n"
                + "Order details: " + orderLink(o) + "\n\n— " + brand();
        String wa = props.seller().whatsapp();
        String body = badge("Delivered", OK)
                + h1("Delivered. We hope you love it!")
                + p("Hi " + esc(first(o)) + ", your order <b style=\"color:" + INK + ";\">" + esc(o.orderNo()) + "</b> has been delivered. "
                + "Every piece is printed and finished by hand, so thank you for supporting a small studio.")
                + steps(3)
                + items(o)
                + p("Something not right? Reply to this email or "
                + (wa != null && !wa.isBlank() ? "<a href=\"https://wa.me/" + esc(wa) + "\" style=\"color:" + INK + ";\">message us on WhatsApp</a>" : "message us")
                + " within 48 hours of delivery with a photo, and we'll make it right.")
                + button("View your order", orderLink(o))
                + small("Share a photo of your piece and tag us. We'd love to see it.");
        return new Email(subject, text, page("Order " + o.orderNo() + " has been delivered.", body));
    }

    public Email cancelled(OrderView o) {
        boolean paid = o.paymentMode() == PaymentMode.ONLINE;
        String subject = "Your order " + o.orderNo() + " has been cancelled";
        String text = "Hi " + first(o) + ",\n\nYour order " + o.orderNo() + " has been cancelled."
                + (paid ? " Your payment of " + rs(o.total()) + " will be refunded to the original payment method within 5–7 working days." : "")
                + "\n\nQuestions? Just reply to this email.\n\n— " + brand();
        String body = badge("Cancelled", MUTED)
                + h1("Your order has been cancelled")
                + p("Hi " + esc(first(o)) + ", your order <b style=\"color:" + INK + ";\">" + esc(o.orderNo()) + "</b> has been cancelled.")
                + (paid ? p("Your payment of <b style=\"color:" + INK + ";\">" + rs(o.total()) + "</b> will be refunded to the original payment method within 5–7 working days.") : "")
                + items(o)
                + p("Questions? Just reply to this email and we'll help.")
                + button("Continue shopping", base() + "/shop");
        return new Email(subject, text, page("Order " + o.orderNo() + " has been cancelled.", body));
    }

    // ---------- building blocks ----------

    private String page(String preheader, String content) {
        var s = props.seller();
        String legal = s.legalName() != null && !s.legalName().isBlank() ? s.legalName() : brand();
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">"
                + "<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap\" rel=\"stylesheet\">"
                + "<title>" + esc(brand()) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:" + SURFACE + ";-webkit-font-smoothing:antialiased;\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">" + esc(preheader) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + SURFACE + ";\"><tr><td align=\"center\" style=\"padding:32px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;\">"
                // header
                + "<tr><td style=\"padding:0 4px 16px;\"><a href=\"" + base() + "\" style=\"text-decoration:none;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td style=\"vertical-align:middle;\"><img src=\"" + base() + "/brand/logo-mark.png\" width=\"52\" height=\"32\" alt=\"\" style=\"display:block;border:0;\"></td>"
                + "<td style=\"vertical-align:middle;padding-left:10px;font-family:" + FONT + ";line-height:1;\">"
                + "<div style=\"font-size:16px;font-weight:700;letter-spacing:3px;color:" + INK + ";\">VINDHYAN</div>"
                + "<div style=\"font-size:9px;letter-spacing:5px;color:" + MUTED + ";margin-top:3px;\">STUDIO</div></td>"
                + "</tr></table></a></td></tr>"
                // card
                + "<tr><td style=\"background:#ffffff;border:1px solid " + LINE + ";border-radius:16px;padding:32px 28px;\">" + content + "</td></tr>"
                // footer
                + "<tr><td style=\"padding:24px 8px 0;font-family:" + FONT + ";font-size:12px;line-height:1.6;color:" + MUTED + ";text-align:center;\">"
                + "Questions? Reply to this email"
                + (s.whatsapp() != null && !s.whatsapp().isBlank() ? " or <a href=\"https://wa.me/" + esc(s.whatsapp()) + "\" style=\"color:" + MUTED + ";\">WhatsApp us</a>" : "")
                + ".<br><a href=\"" + base() + "/shop\" style=\"color:" + MUTED + ";\">Shop</a> &nbsp;·&nbsp; "
                + "<a href=\"" + base() + "/policies/shipping\" style=\"color:" + MUTED + ";\">Shipping</a> &nbsp;·&nbsp; "
                + "<a href=\"" + base() + "/policies/refund\" style=\"color:" + MUTED + ";\">Returns</a> &nbsp;·&nbsp; "
                + "<a href=\"" + base() + "/policies/privacy\" style=\"color:" + MUTED + ";\">Privacy</a>"
                + "<br><br>" + esc(brand()) + (legal.equals(brand()) ? "" : " · a brand of " + esc(legal))
                + (s.address() != null && !s.address().startsWith("Address line") ? "<br>" + esc(s.address()) : "")
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /** Confirmed → Shipped → Delivered tracker, with `current` (1–3) highlighted. */
    private static String steps(int current) {
        String[] labels = {"Confirmed", "Shipped", "Delivered"};
        StringBuilder row = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0 8px;\"><tr>");
        for (int i = 0; i < 3; i++) {
            boolean done = i + 1 <= current;
            row.append("<td width=\"33%\" style=\"padding:0 3px;\">")
                    .append("<div style=\"height:4px;border-radius:4px;background:").append(done ? INK : LINE).append(";\"></div>")
                    .append("<div style=\"font-family:").append(FONT).append(";font-size:12px;padding-top:8px;color:").append(done ? INK : MUTED)
                    .append(";font-weight:").append(i + 1 == current ? "600" : "400").append(";\">").append(labels[i]).append("</div></td>");
        }
        return row.append("</tr></table>").toString();
    }

    private static String items(OrderView o) {
        StringBuilder t = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:20px 0 0;border-top:1px solid " + LINE + ";\">");
        for (OrderView.Item i : o.items()) {
            t.append("<tr><td width=\"64\" style=\"padding:14px 0;border-bottom:1px solid ").append(LINE).append(";vertical-align:top;\">");
            if (i.image() != null) t.append("<img src=\"").append(esc(i.image())).append("\" width=\"52\" height=\"52\" alt=\"\" style=\"display:block;border-radius:8px;object-fit:cover;background:").append(SURFACE).append(";\">");
            else t.append("<div style=\"width:52px;height:52px;border-radius:8px;background:").append(SURFACE).append(";\"></div>");
            t.append("</td><td style=\"padding:14px 12px;border-bottom:1px solid ").append(LINE).append(";font-family:").append(FONT).append(";font-size:14px;color:").append(INK).append(";vertical-align:top;\">")
                    .append(esc(i.name()))
                    .append("<div style=\"color:").append(MUTED).append(";font-size:12px;margin-top:3px;\">Qty ").append(i.qty())
                    .append(custom(i)).append("</div></td>")
                    .append("<td align=\"right\" style=\"padding:14px 0;border-bottom:1px solid ").append(LINE).append(";font-family:").append(FONT)
                    .append(";font-size:14px;font-weight:600;color:").append(INK).append(";vertical-align:top;white-space:nowrap;\">").append(rs(i.lineTotal())).append("</td></tr>");
        }
        return t.append("</table>").toString();
    }

    private static String custom(OrderView.Item i) {
        var c = i.customisation();
        if (c == null) return "";
        StringBuilder s = new StringBuilder();
        if (c.text() != null && !c.text().isBlank()) s.append(" · “").append(esc(c.text())).append("”");
        if (c.colour() != null && !c.colour().isBlank()) s.append(" · ").append(esc(c.colour()));
        if (c.size() != null && !c.size().isBlank()) s.append(" · ").append(esc(c.size()));
        if (c.photos() != null && !c.photos().isEmpty()) s.append(" · ").append(c.photos().size()).append(" photo").append(c.photos().size() == 1 ? "" : "s");
        return s.toString();
    }

    private static String totals(OrderView o, boolean cod) {
        StringBuilder t = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:12px 0 0;font-family:" + FONT + ";font-size:14px;color:" + MUTED + ";\">");
        t.append(line("Items", rs(o.subtotal())));
        t.append(line("Shipping", o.shippingFee() == null || o.shippingFee().signum() == 0 ? "Free" : rs(o.shippingFee())));
        if (o.discount() != null && o.discount().signum() > 0) t.append(line("Discount", "−" + rs(o.discount())));
        t.append("<tr><td style=\"padding:10px 0 0;color:").append(INK).append(";font-weight:600;font-size:16px;\">Total</td>")
                .append("<td align=\"right\" style=\"padding:10px 0 0;color:").append(INK).append(";font-weight:700;font-size:16px;\">").append(rs(o.total())).append("</td></tr>");
        t.append("<tr><td colspan=\"2\" align=\"right\" style=\"padding-top:2px;font-size:12px;color:").append(cod ? ACCENT : OK).append(";\">")
                .append(cod ? "Pay on delivery (cash or UPI)" : "Paid online").append("</td></tr>");
        return t.append("</table>").toString();
    }

    private static String line(String label, String value) {
        return "<tr><td style=\"padding:3px 0;\">" + label + "</td><td align=\"right\" style=\"padding:3px 0;color:" + INK + ";\">" + value + "</td></tr>";
    }

    private static String address(OrderView o) {
        var a = o.address();
        if (a == null) return "";
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0 0;\"><tr><td style=\"font-family:" + FONT
                + ";font-size:13px;line-height:1.6;color:" + MUTED + ";\"><div style=\"color:" + INK + ";font-weight:600;font-size:13px;margin-bottom:2px;\">Delivering to</div>"
                + esc(a.name()) + "<br>" + esc(a.oneLine()) + "</td></tr></table>";
    }

    private static String badge(String label, String colour) {
        return "<div style=\"font-family:" + FONT + ";font-size:12px;font-weight:600;color:" + colour + ";margin-bottom:10px;\">● " + esc(label) + "</div>";
    }

    private static String h1(String s) {
        return "<h1 style=\"margin:0 0 10px;font-family:" + FONT + ";font-size:24px;line-height:1.25;font-weight:700;letter-spacing:-0.4px;color:" + INK + ";\">" + s + "</h1>";
    }

    private static String p(String s) {
        return "<p style=\"margin:0 0 12px;font-family:" + FONT + ";font-size:15px;line-height:1.6;color:#4b5563;\">" + s + "</p>";
    }

    private static String small(String s) {
        return "<p style=\"margin:20px 0 0;font-family:" + FONT + ";font-size:12px;line-height:1.6;color:" + MUTED + ";\">" + s + "</p>";
    }

    private static String button(String label, String href) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:28px 0 4px;\"><tr><td style=\"background:" + INK + ";border-radius:8px;\">"
                + "<a href=\"" + esc(href) + "\" style=\"display:inline-block;padding:13px 24px;font-family:" + FONT
                + ";font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;\">" + esc(label) + "</a></td></tr></table>";
    }

    private String orderLink(OrderView o) {
        return base() + "/account/orders/" + o.id();
    }

    private String base() {
        String u = props.frontendUrl();
        return u == null ? "" : u.replaceAll("/+$", "");
    }

    private String brand() {
        return props.seller().name() == null ? "Vindhyan Studio" : props.seller().name();
    }

    private static String first(OrderView o) {
        String n = o.address() != null ? o.address().name() : null;
        if (n == null || n.isBlank()) return "there";
        return n.trim().split("\\s+")[0];
    }

    static String rs(BigDecimal v) {
        if (v == null) return "₹0";
        BigDecimal s = v.stripTrailingZeros();
        return "₹" + (s.scale() <= 0 ? String.format(Locale.ENGLISH, "%,d", s.longValue()) : String.format(Locale.ENGLISH, "%,.2f", v));
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
