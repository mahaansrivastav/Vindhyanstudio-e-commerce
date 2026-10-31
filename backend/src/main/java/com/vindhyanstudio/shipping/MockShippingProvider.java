/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.shipping;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local/dev stand-in for Shiprocket. Pincodes starting with 9 are treated as not serviceable, to test that path. */
@Component
@ConditionalOnProperty(name = "app.shipping.provider", havingValue = "mock", matchIfMissing = true)
public class MockShippingProvider implements ShippingProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Serviceability check(String pincode, int weightG, boolean cod) {
        if (pincode.startsWith("9")) return new Serviceability(false, null, false);
        return new Serviceability(true, 3 + Math.abs(pincode.hashCode() % 4), true);
    }

    @Override
    public Created createOrder(Request r) {
        return new Created("MOCK-SR-" + r.orderNo(), "MOCK-SHIP-" + r.orderNo());
    }

    @Override
    public Booked book(String shipmentId) {
        String awb = "MOCK" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        return new Booked(awb, "Mock Express", null, "https://shiprocket.co/tracking/" + awb);
    }
}
