/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.order;

import com.vindhyanstudio.customer.Address;

/** Copy of the delivery address frozen on the order, so later edits don't change past orders. */
public record AddressSnapshot(String name, String phone, String line1, String line2, String city, String state, String pincode) {

    public static AddressSnapshot of(Address a) {
        return new AddressSnapshot(a.getName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(), a.getState(), a.getPincode());
    }

    public String oneLine() {
        return line1 + (line2 == null || line2.isBlank() ? "" : ", " + line2) + ", " + city + ", " + state + " - " + pincode;
    }
}
