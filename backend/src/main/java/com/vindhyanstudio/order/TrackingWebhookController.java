package com.vindhyanstudio.order;

import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.shipping.ShipmentRepository;
import com.vindhyanstudio.shipping.ShippingService;

import tools.jackson.databind.JsonNode;

/**
 * Shiprocket tracking webhook (BRD FR-24). Register in Shiprocket → Settings → API → Webhooks:
 * URL {APP_PUBLIC_BASE_URL}/api/webhooks/tracking, token = SHIPROCKET_WEBHOOK_TOKEN.
 * (Shiprocket rejects URLs containing the word "shiprocket", hence the neutral path.)
 */
@RestController
public class TrackingWebhookController {

    private final AppProperties props;
    private final ShipmentRepository shipments;
    private final ShippingService shipping;
    private final OrderRepository orderRepo;
    private final OrderService orders;

    public TrackingWebhookController(AppProperties props, ShipmentRepository shipments, ShippingService shipping,
                                     OrderRepository orderRepo, OrderService orders) {
        this.props = props;
        this.shipments = shipments;
        this.shipping = shipping;
        this.orderRepo = orderRepo;
        this.orders = orders;
    }

    @PostMapping("/api/webhooks/tracking")
    ResponseEntity<Void> tracking(@RequestBody JsonNode body, @RequestHeader(name = "x-api-key", required = false) String token) {
        String expected = props.shipping().shiprocket().webhookToken();
        if (expected == null || expected.isBlank() || !expected.equals(token)) return ResponseEntity.status(401).build();

        String awb = body.path("awb").asString();
        String status = body.path("current_status").asString().toUpperCase(Locale.ROOT);
        Long orderId = shipments.findByAwb(awb).map(s -> s.getOrderId())
                .or(() -> orderRepo.findByOrderNo(body.path("order_id").asString()).map(Order::getId))
                .orElse(null);
        if (orderId == null || status.isEmpty()) return ResponseEntity.ok().build();

        shipping.providerStatus(orderId, status);
        OrderStatus mapped = status.startsWith("RTO") ? OrderStatus.RTO
                : status.equals("DELIVERED") ? OrderStatus.DELIVERED
                : status.contains("PICKED UP") || status.contains("IN TRANSIT") || status.equals("SHIPPED")
                  || status.contains("OUT FOR DELIVERY") ? OrderStatus.DISPATCHED
                : null;
        if (mapped != null) {
            Order o = orders.get(orderId);
            if (o.getStatus() != mapped && o.getStatus().canMoveTo(mapped)) {
                orders.changeStatus(orderId, mapped, "Courier: " + status, "courier");
            }
        }
        return ResponseEntity.ok().build();
    }
}
