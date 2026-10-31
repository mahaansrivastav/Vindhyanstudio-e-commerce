package com.vindhyanstudio.order;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.vindhyanstudio.catalogue.Product;
import com.vindhyanstudio.catalogue.ProductRepository;
import com.vindhyanstudio.catalogue.sync.CatalogueSource;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.invoice.InvoiceService;
import com.vindhyanstudio.notify.EmailTemplates;
import com.vindhyanstudio.notify.NotificationService;
import com.vindhyanstudio.shipping.ShippingService;

/**
 * The automation that runs once money is in: invoice → Shiprocket order → messages → stock sync.
 * Each step is independent; a failing step is logged and can be retried from admin.
 */
@Component
public class FulfilmentListener {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentListener.class);

    private final OrderService orders;
    private final InvoiceService invoices;
    private final ShippingService shipping;
    private final NotificationService notify;
    private final ProductRepository products;
    private final CatalogueSource catalogue;
    private final AppProperties props;
    private final EmailTemplates templates;

    public FulfilmentListener(OrderService orders, InvoiceService invoices, ShippingService shipping, NotificationService notify,
                              ProductRepository products, CatalogueSource catalogue, AppProperties props, EmailTemplates templates) {
        this.templates = templates;
        this.orders = orders;
        this.invoices = invoices;
        this.shipping = shipping;
        this.notify = notify;
        this.products = products;
        this.catalogue = catalogue;
        this.props = props;
    }

    @Async
    @TransactionalEventListener
    public void onConfirmed(OrderConfirmedEvent e) {
        step("invoice", e.orderId(), () -> invoices.generate(e.orderId()));
        step("shipment", e.orderId(), () -> {
            shipping.create(e.orderId());
            if (shipping.autoBook()) orders.changeStatus(e.orderId(), OrderStatus.READY, "Courier booked automatically", "system");
        });
        step("notify", e.orderId(), () -> {
            OrderView o = orders.view(e.orderId(), true);
            String link = props.frontendUrl() + "/account/orders/" + o.id();
            String items = o.items().stream().map(i -> "• " + i.name() + " × " + i.qty() + "  Rs." + i.lineTotal())
                    .collect(Collectors.joining("\n"));
            notify.email(o.customer().email(), templates.orderConfirmed(o), o.id());
            notify.sms(o.customer().phone(), "Vindhyan Studio: order " + o.orderNo() + " confirmed. Total Rs." + o.total()
                    + ". Track: " + link, o.id());
            notify.email(props.seller().email(), "New order " + o.orderNo() + " — Rs." + o.total(),
                    "New " + o.paymentMode() + " order " + o.orderNo() + " from " + nz(o.address().name()) + ", " + o.address().city()
                            + "\n\n" + items + "\n\nOpen in admin: " + props.frontendUrl() + "/admin/orders/" + o.id(), o.id());
        });
        step("stock write-back", e.orderId(), () -> {
            OrderView o = orders.view(e.orderId(), false);
            Map<String, Integer> stock = products.findAllById(o.items().stream().map(OrderView.Item::sku).toList()).stream()
                    .collect(Collectors.toMap(Product::getSku, Product::getStock));
            catalogue.writeStock(stock);
        });
    }

    @Async
    @TransactionalEventListener
    public void onStatusChanged(OrderStatusChangedEvent e) {
        if (e.status() == OrderStatus.CANCELLED) {
            step("shipment cancel", e.orderId(), () -> {
                String note = shipping.cancel(e.orderId());
                if (note != null) orders.addNote(e.orderId(), note, "system");
            });
        }
        step("status notify", e.orderId(), () -> {
            OrderView o = orders.view(e.orderId(), true);
            String link = props.frontendUrl() + "/account/orders/" + o.id();
            String msg = switch (e.status()) {
                case DISPATCHED -> "Your order " + o.orderNo() + " is on its way"
                        + (o.shipment() != null && o.shipment().trackingUrl() != null ? ". Track: " + o.shipment().trackingUrl() : ".");
                case DELIVERED -> "Your order " + o.orderNo() + " has been delivered. Enjoy!";
                case CANCELLED -> "Your order " + o.orderNo() + " has been cancelled. Any payment will be refunded.";
                default -> null;
            };
            if (msg == null) return;
            EmailTemplates.Email mail = switch (e.status()) {
                case DISPATCHED -> templates.shipped(o);
                case DELIVERED -> templates.delivered(o);
                default -> templates.cancelled(o);
            };
            notify.email(o.customer().email(), mail, o.id());
            notify.sms(o.customer().phone(), "Vindhyan Studio: " + msg, o.id());
        });
    }

    private interface Step {
        void run() throws Exception;
    }

    private static void step(String name, Long orderId, Step s) {
        try {
            s.run();
        } catch (Exception ex) {
            log.error("Fulfilment step '{}' failed for order {}", name, orderId, ex);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
