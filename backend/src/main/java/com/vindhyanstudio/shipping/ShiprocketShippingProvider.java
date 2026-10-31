/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.shipping;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.vindhyanstudio.config.AppProperties;

import tools.jackson.databind.JsonNode;

/**
 * Shiprocket API v2. Docs: https://apidocs.shiprocket.in
 * Setup: Shiprocket dashboard → Settings → API → create an API user; add a pickup address and
 * put its nickname in SHIPROCKET_PICKUP_LOCATION. Env: SHIPPING_PROVIDER=shiprocket,
 * SHIPROCKET_EMAIL, SHIPROCKET_PASSWORD, SHIPROCKET_PICKUP_LOCATION, SHIPROCKET_PICKUP_PINCODE,
 * SHIPROCKET_WEBHOOK_TOKEN (sent back by Shiprocket as the x-api-key header).
 */
@Component
@ConditionalOnProperty(name = "app.shipping.provider", havingValue = "shiprocket")
public class ShiprocketShippingProvider implements ShippingProvider {

    private static final Logger log = LoggerFactory.getLogger(ShiprocketShippingProvider.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppProperties.Shipping.Shiprocket cfg;
    private final RestClient http = RestClient.builder().baseUrl("https://apiv2.shiprocket.in/v1/external").build();
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public ShiprocketShippingProvider(AppProperties props) {
        this.cfg = props.shipping().shiprocket();
        if (cfg.email() == null || cfg.email().isBlank() || cfg.password() == null || cfg.password().isBlank()) {
            throw new IllegalStateException("SHIPROCKET_EMAIL and SHIPROCKET_PASSWORD must be set when SHIPPING_PROVIDER=shiprocket");
        }
    }

    @Override
    public String name() {
        return "shiprocket";
    }

    /** Tokens last 10 days; refresh a day early. */
    private synchronized String token() {
        if (token == null || Instant.now().isAfter(tokenExpiry)) {
            JsonNode res = http.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", cfg.email(), "password", cfg.password()))
                    .retrieve().body(JsonNode.class);
            token = res.path("token").asString();
            tokenExpiry = Instant.now().plus(Duration.ofDays(9));
        }
        return token;
    }

    @Override
    public Serviceability check(String pincode, int weightG, boolean cod) {
        JsonNode res = http.get()
                .uri(u -> u.path("/courier/serviceability/")
                        .queryParam("pickup_postcode", cfg.pickupPincode())
                        .queryParam("delivery_postcode", pincode)
                        .queryParam("weight", Math.max(weightG, 50) / 1000.0)
                        .queryParam("cod", cod ? 1 : 0)
                        .build())
                .header("Authorization", "Bearer " + token())
                .retrieve().body(JsonNode.class);
        JsonNode couriers = res.path("data").path("available_courier_companies");
        if (!couriers.isArray() || couriers.isEmpty()) return new Serviceability(false, null, false);
        Integer best = null;
        boolean codOk = false;
        for (JsonNode c : couriers) {
            String eta = c.path("estimated_delivery_days").asString();
            try {
                int d = Integer.parseInt(eta.trim());
                if (best == null || d < best) best = d;
            } catch (NumberFormatException ignored) {
                // some couriers return blank
            }
            if (c.path("cod").asInt() == 1) codOk = true;
        }
        return new Serviceability(true, best, codOk);
    }

    @Override
    public Created createOrder(Request r) {
        var a = r.address();
        String[] name = a.name().trim().split("\\s+", 2);
        List<Map<String, Object>> items = r.items().stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", i.name());
            m.put("sku", i.sku());
            m.put("units", i.units());
            m.put("selling_price", i.unitPrice());
            if (i.hsn() != null && !i.hsn().isBlank()) m.put("hsn", i.hsn());
            return m;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", r.orderNo());
        body.put("order_date", r.orderDate().format(DATE));
        body.put("pickup_location", cfg.pickupLocation());
        body.put("billing_customer_name", name[0]);
        body.put("billing_last_name", name.length > 1 ? name[1] : "");
        body.put("billing_address", a.line1());
        body.put("billing_address_2", a.line2() == null ? "" : a.line2());
        body.put("billing_city", a.city());
        body.put("billing_pincode", a.pincode());
        body.put("billing_state", a.state());
        body.put("billing_country", "India");
        body.put("billing_email", r.email() == null ? "" : r.email());
        body.put("billing_phone", a.phone());
        body.put("shipping_is_billing", true);
        body.put("order_items", items);
        body.put("payment_method", r.cod() ? "COD" : "Prepaid");
        body.put("shipping_charges", r.shippingFee());
        body.put("sub_total", r.total());
        body.put("length", r.lengthCm());
        body.put("breadth", r.widthCm());
        body.put("height", r.heightCm());
        body.put("weight", Math.max(r.weightKg(), 0.05));
        JsonNode res = http.post().uri("/orders/create/adhoc").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token())
                .body(body).retrieve().body(JsonNode.class);
        String orderId = res.path("order_id").asString();
        String shipmentId = res.path("shipment_id").asString();
        if (shipmentId.isEmpty()) throw new IllegalStateException("Shiprocket did not return a shipment_id: " + res);
        return new Created(orderId, shipmentId);
    }

    @Override
    public Booked book(String shipmentId) {
        Map<String, Object> awbBody = new LinkedHashMap<>();
        awbBody.put("shipment_id", shipmentId);
        if (cfg.courierId() != null && !cfg.courierId().isBlank()) awbBody.put("courier_id", cfg.courierId().trim());
        JsonNode awbRes = http.post().uri("/courier/assign/awb").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token())
                .body(awbBody).retrieve().body(JsonNode.class);
        JsonNode data = awbRes.path("response").path("data");
        String awb = data.path("awb_code").asString();
        if (awb.isEmpty()) throw new IllegalStateException("AWB not assigned: " + awbRes);
        String courier = data.path("courier_name").asString();

        try {
            http.post().uri("/courier/generate/pickup").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token())
                    .body(Map.of("shipment_id", List.of(shipmentId))).retrieve().toBodilessEntity();
        } catch (Exception e) {
            // Pickup can also be scheduled from the dashboard; don't lose the AWB because of it.
            log.warn("Shiprocket pickup request failed for shipment {}", shipmentId, e);
        }

        String label = null;
        try {
            JsonNode labelRes = http.post().uri("/courier/generate/label").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token())
                    .body(Map.of("shipment_id", List.of(shipmentId))).retrieve().body(JsonNode.class);
            label = labelRes.path("label_url").asString();
        } catch (Exception e) {
            log.warn("Shiprocket label generation failed for shipment {}", shipmentId, e);
        }
        return new Booked(awb, courier, label, "https://shiprocket.co/tracking/" + awb);
    }

    @Override
    public void cancel(String providerOrderId) {
        http.post().uri("/orders/cancel").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token())
                .body(Map.of("ids", List.of(providerOrderId))).retrieve().toBodilessEntity();
    }
}
