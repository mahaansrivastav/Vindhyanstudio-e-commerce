/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.shipping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShippingController {

    private final ShippingService shipping;

    public ShippingController(ShippingService shipping) {
        this.shipping = shipping;
    }

    /** Pincode check on cart/checkout (BRD FR-21). leadDays = production time of made-to-order items. */
    @GetMapping("/api/shipping/serviceability")
    Map<String, Object> check(@RequestParam String pincode,
                              @RequestParam(defaultValue = "200") int weightG,
                              @RequestParam(defaultValue = "0") int leadDays) {
        ShippingProvider.Serviceability s = shipping.check(pincode.trim(), weightG, false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pincode", pincode.trim());
        m.put("serviceable", s.serviceable());
        m.put("codAvailable", s.codAvailable());
        if (s.serviceable() && s.etaDays() != null) {
            m.put("etaDays", s.etaDays() + Math.max(leadDays, 0));
            m.put("expectedBy", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(s.etaDays() + Math.max(leadDays, 0) + 1L).toString());
        }
        return m;
    }
}
