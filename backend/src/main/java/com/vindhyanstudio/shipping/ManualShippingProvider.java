package com.vindhyanstudio.shipping;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SHIPPING_PROVIDER=manual: no courier integration yet. Every pincode is accepted and the owner
 * books the courier himself; "Packed" just records the step (no AWB or tracking link is invented).
 */
@Component
@ConditionalOnProperty(name = "app.shipping.provider", havingValue = "manual")
public class ManualShippingProvider implements ShippingProvider {

    @Override
    public String name() {
        return "manual";
    }

    @Override
    public Serviceability check(String pincode, int weightG, boolean cod) {
        return new Serviceability(true, 6, true);
    }

    @Override
    public Created createOrder(Request r) {
        return new Created("MANUAL-" + r.orderNo(), "MANUAL-" + r.orderNo());
    }

    @Override
    public Booked book(String shipmentId) {
        return new Booked(null, "Booked by Vindhyan Studio", null, null);
    }
}
