package com.vindhyanstudio.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** Order as returned to the storefront (customer fields) and admin (adds contact, label, errors). */
public record OrderView(
        Long id, String orderNo, OrderStatus status, PaymentMode paymentMode, OffsetDateTime createdAt,
        AddressSnapshot address, List<Item> items, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal discount,
        BigDecimal total, List<Event> events, ShipmentInfo shipment, String invoiceNo, boolean canPay,
        Contact customer, Set<OrderStatus> nextStatuses) {

    public record Item(String sku, String name, String slug, String image, BigDecimal unitPrice, BigDecimal mrp, int qty,
                       BigDecimal lineTotal, Customisation customisation) {}

    public record Event(OrderStatus status, String note, String actor, OffsetDateTime at) {}

    public record ShipmentInfo(String status, String courier, String awb, String trackingUrl, String labelUrl, String error) {}

    public record Contact(String name, String email, String phone) {}

    public record Summary(Long id, String orderNo, OrderStatus status, PaymentMode paymentMode, BigDecimal total,
                          OffsetDateTime createdAt, int itemCount, String firstItem, String customerName, String city,
                          boolean customised) {}
}
