/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.common.CurrentUser;
import com.vindhyanstudio.customer.CustomerRepository;
import com.vindhyanstudio.invoice.Invoice;
import com.vindhyanstudio.invoice.InvoiceService;
import com.vindhyanstudio.payment.PaymentService;
import com.vindhyanstudio.storage.StorageService;

@RestController
public class OrderController {

    private final OrderService orders;
    private final PaymentService payments;
    private final PricingService pricing;
    private final InvoiceService invoices;
    private final CustomerRepository customers;
    private final StorageService storage;

    public OrderController(OrderService orders, PaymentService payments, PricingService pricing, InvoiceService invoices,
                           CustomerRepository customers, StorageService storage) {
        this.orders = orders;
        this.payments = payments;
        this.pricing = pricing;
        this.invoices = invoices;
        this.customers = customers;
        this.storage = storage;
    }

    public record QuoteRequest(List<PricingService.LineRequest> items) {}

    @PostMapping("/api/cart/quote")
    PricingService.Quote quote(@RequestBody QuoteRequest body) {
        return pricing.quote(body.items() == null ? List.of() : body.items());
    }

    @PostMapping("/api/orders")
    Map<String, Object> create(@RequestBody OrderService.CreateOrderRequest body) {
        long me = CurrentUser.id();
        Order o = orders.create(me, body);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", o.getId());
        res.put("orderNo", o.getOrderNo());
        // Online orders continue on the payment page, which calls /api/orders/{id}/pay.
        res.put("paymentRequired", o.getPaymentMode() == PaymentMode.ONLINE);
        return res;
    }

    /** Retry payment for an order still waiting for it (BRD FR-17). */
    @PostMapping("/api/orders/{id}/pay")
    PaymentService.PaymentInit pay(@PathVariable Long id) {
        long me = CurrentUser.id();
        if (!orders.isOwner(id, me)) throw ApiException.notFound("Order not found");
        return payments.start(orders.get(id), customers.findById(me).orElseThrow());
    }

    @PostMapping("/api/orders/{id}/cod")
    Map<String, Object> switchToCod(@PathVariable Long id) {
        orders.switchToCod(id, CurrentUser.id());
        return Map.of("orderId", id);
    }

    @GetMapping("/api/orders")
    List<OrderView.Summary> list() {
        return orders.listForCustomer(CurrentUser.id());
    }

    @GetMapping("/api/orders/{id}")
    OrderView get(@PathVariable Long id) {
        if (!orders.isOwner(id, CurrentUser.id())) throw ApiException.notFound("Order not found");
        return orders.view(id, false);
    }

    @GetMapping("/api/orders/{id}/invoice")
    ResponseEntity<byte[]> invoice(@PathVariable Long id) {
        if (!CurrentUser.isAdmin() && !orders.isOwner(id, CurrentUser.id())) throw ApiException.notFound("Order not found");
        Invoice inv = invoices.forOrder(id).orElseThrow(() -> ApiException.notFound("Invoice is being generated, try again in a moment"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(inv.getInvoiceNo().replace('/', '-') + ".pdf").build().toString())
                .body(invoices.pdf(inv));
    }

    /** Private files: the customer's own uploads, or anything for admins. */
    @GetMapping("/api/files")
    ResponseEntity<byte[]> file(@RequestParam String key) {
        boolean own = key.startsWith("private/uploads/" + CurrentUser.id() + "/");
        if (key.contains("..") || (!own && !CurrentUser.isAdmin())) throw ApiException.forbidden("Not allowed");
        if (!storage.exists(key)) throw ApiException.notFound("File not found");
        MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG : key.endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(storage.get(key));
    }
}
