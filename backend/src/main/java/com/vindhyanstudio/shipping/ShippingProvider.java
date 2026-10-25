package com.vindhyanstudio.shipping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.vindhyanstudio.order.AddressSnapshot;

/**
 * A courier aggregator. v1: {@link MockShippingProvider} and {@link ShiprocketShippingProvider},
 * selected with app.shipping.provider.
 */
public interface ShippingProvider {

    String name();

    Serviceability check(String pincode, int weightG, boolean cod) throws Exception;

    /** Creates the shipment order at the provider (called right after payment). */
    Created createOrder(Request request) throws Exception;

    /** Books a courier, schedules pickup and fetches the label (called when the owner marks the order READY). */
    Booked book(String shipmentId) throws Exception;

    record Serviceability(boolean serviceable, Integer etaDays, boolean codAvailable) {}

    record Item(String name, String sku, int units, BigDecimal unitPrice, String hsn) {}

    record Request(String orderNo, LocalDateTime orderDate, AddressSnapshot address, String email, List<Item> items,
                   boolean cod, BigDecimal subTotal, BigDecimal shippingFee, BigDecimal total,
                   double weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}

    record Created(String providerOrderId, String shipmentId) {}

    record Booked(String awb, String courier, String labelUrl, String trackingUrl) {}
}
