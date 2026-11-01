/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.order.AddressSnapshot;
import com.vindhyanstudio.order.Customisation;
import com.vindhyanstudio.order.OrderStatus;
import com.vindhyanstudio.order.OrderView;
import com.vindhyanstudio.order.PaymentMode;

/** Renders every email; previews are written to target/email-previews/ for a visual check. */
class EmailTemplatesTest {

    private static final String IMG = "https://udozexopfprxbxfzglyr.supabase.co/storage/v1/object/public/catalogue/products/";

    private final EmailTemplates t = new EmailTemplates(new AppProperties(null, "https://vindhyanstudio.in", null,
            new AppProperties.Seller("Vindhyan Studio", "MORVIN INTEGRATED SOLUTIONS", "Bhagatpuwa, Padari Bazar, Gorakhpur, Uttar Pradesh - 273014",
                    "Uttar Pradesh", "09RNWPS5738H1ZK", "vindhyanstudio@gmail.com", "+91 94559 38254", "919455938254", "3926"),
            null, null, null, null, null, null, null, null, null));

    private static OrderView order(PaymentMode mode) {
        var items = List.of(
                new OrderView.Item("VS-013", "Customisable Keychain", "k", IMG + "VS-013/13.jpg", new BigDecimal("70"), new BigDecimal("99"), 2,
                        new BigDecimal("140"), new Customisation("Ananya <3", "Pink", "Script", null, null)),
                new OrderView.Item("VS-021", "Baby Hanuman with Gada", "h", IMG + "VS-021/21.jpg", new BigDecimal("156"), new BigDecimal("249"), 1,
                        new BigDecimal("156"), null));
        return new OrderView(42L, "VS10042", OrderStatus.CONFIRMED, mode, OffsetDateTime.now(),
                new AddressSnapshot("Priya Sharma", "9876543210", "12 MG Road", "Near City Mall", "Lucknow", "Uttar Pradesh", "226001"),
                items, new BigDecimal("296"), new BigDecimal("60"), BigDecimal.ZERO, new BigDecimal("356"), List.of(),
                new OrderView.ShipmentInfo("SHIPPED", "Delhivery", "1234567890123", "https://www.delhivery.com/track/package/1234567890123", null, null),
                "VS/2026-27/00042", false, null, null);
    }

    @Test
    void rendersAllEmails() throws Exception {
        Path dir = Path.of("target/email-previews");
        Files.createDirectories(dir);
        var all = List.of(
                new Object[] {"otp", t.otp("482913", 5)},
                new Object[] {"confirmed-cod", t.orderConfirmed(order(PaymentMode.COD))},
                new Object[] {"confirmed-paid", t.orderConfirmed(order(PaymentMode.ONLINE))},
                new Object[] {"shipped", t.shipped(order(PaymentMode.COD))},
                new Object[] {"delivered", t.delivered(order(PaymentMode.ONLINE))},
                new Object[] {"cancelled", t.cancelled(order(PaymentMode.ONLINE))});
        for (Object[] row : all) {
            EmailTemplates.Email e = (EmailTemplates.Email) row[1];
            assertFalse(e.subject().isBlank());
            assertFalse(e.text().contains("<"), "plain text must not contain HTML");
            assertTrue(e.html().startsWith("<!doctype html>"));
            Files.writeString(dir.resolve(row[0] + ".html"), e.html());
        }
        // Customer-entered text is escaped.
        assertTrue(t.orderConfirmed(order(PaymentMode.COD)).html().contains("Ananya &lt;3"));
        assertEquals("₹1,299", EmailTemplates.rs(new BigDecimal("1299.00")));
    }
}
