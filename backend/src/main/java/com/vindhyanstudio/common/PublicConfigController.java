package com.vindhyanstudio.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vindhyanstudio.config.AppProperties;

/** Non-secret settings the storefront needs (shipping rule, COD, payment provider). */
@RestController
public class PublicConfigController {

    private final AppProperties props;

    public PublicConfigController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/api/config")
    Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storeName", props.seller().name());
        // Business details shown on the payment, contact and policy pages (payment gateways check these).
        m.put("sellerAddress", props.seller().address());
        m.put("legalName", props.seller().legalName());
        m.put("gstin", props.seller().gstin());
        m.put("sellerEmail", props.seller().email());
        m.put("sellerPhone", props.seller().phone());
        m.put("whatsapp", props.seller().whatsapp());
        m.put("shippingFlatFee", props.pricing().shippingFlatFee());
        m.put("freeShippingAbove", props.pricing().freeShippingAbove());
        m.put("codEnabled", props.pricing().codEnabled());
        m.put("codMaxOrderValue", props.pricing().codMaxOrderValue());
        m.put("paymentProvider", props.payment().provider());
        m.put("devOtp", props.auth().exposeOtp());
        // Mobile OTP login is only offered once a real SMS sender is configured (or in local dev).
        m.put("phoneLogin", props.auth().exposeOtp() || !"console".equals(props.notifications().sms()));
        return m;
    }
}
