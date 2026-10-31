/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.admin;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.vindhyanstudio.catalogue.sync.CatalogueSyncService;
import com.vindhyanstudio.catalogue.sync.SyncRun;
import com.vindhyanstudio.catalogue.sync.SyncRunRepository;
import com.vindhyanstudio.order.OrderService;
import com.vindhyanstudio.order.OrderStatus;
import com.vindhyanstudio.order.OrderView;
import com.vindhyanstudio.shipping.ShippingService;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.payment.PaymentService;

/** Owner screens (S10). Everything under /api/admin requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final OrderService orders;
    private final ShippingService shipping;
    private final CatalogueSyncService sync;
    private final SyncRunRepository runs;
    private final PaymentService payments;
    private final AppProperties props;

    public AdminController(OrderService orders, ShippingService shipping, CatalogueSyncService sync, SyncRunRepository runs,
                           PaymentService payments, AppProperties props) {
        this.payments = payments;
        this.props = props;
        this.orders = orders;
        this.shipping = shipping;
        this.sync = sync;
        this.runs = runs;
    }

    public record StatusChange(OrderStatus status, String note) {}

    public record SyncRunDto(Long id, String startedAt, String finishedAt, int rowsOk, int rowsFailed, List<SyncRun.Issue> issues) {
        static SyncRunDto of(SyncRun r) {
            return new SyncRunDto(r.getId(), String.valueOf(r.getStartedAt()), String.valueOf(r.getFinishedAt()),
                    r.getRowsOk(), r.getRowsFailed(), r.getIssues());
        }
    }

    @GetMapping("/summary")
    Map<String, Object> summary() {
        return orders.adminSummary();
    }

    @GetMapping("/orders")
    List<OrderView.Summary> orders(@RequestParam(required = false) OrderStatus status) {
        return orders.listForAdmin(status);
    }

    @GetMapping("/orders/{id}")
    OrderView order(@PathVariable Long id) {
        return orders.view(id, true);
    }

    @PostMapping("/orders/{id}/status")
    OrderView changeStatus(@PathVariable Long id, @RequestBody StatusChange body) {
        orders.changeStatus(id, body.status(), body.note(), "admin");
        if (body.status() == OrderStatus.CANCELLED && props.payment().refundOnCancel()) payments.refundIfPaid(id);
        return orders.view(id, true);
    }

    @PostMapping("/orders/{id}/shipment/retry")
    OrderView retryShipment(@PathVariable Long id) {
        shipping.create(id);
        return orders.view(id, true);
    }

    @PostMapping("/catalogue/sync")
    SyncRunDto runSync() {
        return SyncRunDto.of(sync.sync());
    }

    @GetMapping("/catalogue/sync-runs")
    List<SyncRunDto> syncRuns() {
        return runs.findTop10ByOrderByIdDesc().stream().map(SyncRunDto::of).toList();
    }
}
