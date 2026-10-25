package com.vindhyanstudio.order;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vindhyanstudio.catalogue.Product;
import com.vindhyanstudio.catalogue.ProductRepository;
import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.customer.Address;
import com.vindhyanstudio.customer.AddressRepository;
import com.vindhyanstudio.customer.Customer;
import com.vindhyanstudio.customer.CustomerRepository;
import com.vindhyanstudio.invoice.InvoiceService;
import com.vindhyanstudio.shipping.Shipment;
import com.vindhyanstudio.shipping.ShippingService;
import com.vindhyanstudio.storage.StorageService;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final AddressRepository addresses;
    private final CustomerRepository customers;
    private final PricingService pricing;
    private final ShippingService shipping;
    private final InvoiceService invoices;
    private final StorageService storage;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public OrderService(OrderRepository orders, ProductRepository products, AddressRepository addresses,
                        CustomerRepository customers, PricingService pricing, ShippingService shipping,
                        InvoiceService invoices, StorageService storage, ApplicationEventPublisher events, AppProperties props) {
        this.orders = orders;
        this.products = products;
        this.addresses = addresses;
        this.customers = customers;
        this.pricing = pricing;
        this.shipping = shipping;
        this.invoices = invoices;
        this.storage = storage;
        this.events = events;
        this.props = props;
    }

    public record CreateOrderRequest(Long addressId, PaymentMode paymentMode, List<PricingService.LineRequest> items) {}

    @Transactional
    public Order create(long customerId, CreateOrderRequest req) {
        if (req.items() == null || req.items().isEmpty()) throw ApiException.badRequest("Your cart is empty");
        if (req.paymentMode() == null) throw ApiException.badRequest("Choose a payment method");
        Address address = addresses.findByIdAndCustomerIdAndDeletedFalse(req.addressId() == null ? -1 : req.addressId(), customerId)
                .orElseThrow(() -> ApiException.badRequest("Choose a delivery address"));
        Customer customer = customers.findById(customerId).orElseThrow();
        if (customer.getName() == null || customer.getName().isBlank()) {
            customer.setName(address.getName());
        }

        PricingService.Quote quote = pricing.quote(req.items());
        if (!quote.valid()) {
            String problem = quote.lines().stream().filter(l -> l.problem() != null)
                    .map(l -> l.name() + ": " + l.problem()).findFirst().orElse("Please review your cart");
            throw ApiException.badRequest(problem);
        }
        if (req.paymentMode() == PaymentMode.COD && !quote.codAvailable()) {
            throw ApiException.badRequest("Cash on delivery is not available for this order");
        }
        if (!shipping.check(address.getPincode(), 200, req.paymentMode() == PaymentMode.COD).serviceable()) {
            throw ApiException.badRequest("Sorry, we can't deliver to pincode " + address.getPincode() + " yet");
        }

        Map<String, Product> bySku = products.findAllById(req.items().stream().map(PricingService.LineRequest::sku).toList())
                .stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        Order order = new Order(customerId, AddressSnapshot.of(address), req.paymentMode());
        for (PricingService.LineRequest line : req.items()) {
            Product p = bySku.get(line.sku());
            pricing.validateCustomisation(p, line.customisation(), customerId);
            order.getItems().add(new OrderItem(p.getSku(), p.getName(), p.getSellingPrice(), p.getMrp(), line.qty(),
                    p.getCategory().getHsnCode(), p.getCategory().getGstRate(), line.customisation()));
        }
        order.setSubtotal(quote.subtotal());
        order.setShippingFee(quote.shippingFee());
        order.setTotal(quote.total());
        order = orders.save(order);
        order.setOrderNo("VS" + (10000 + order.getId()));

        if (req.paymentMode() == PaymentMode.COD) {
            confirm(order.getId(), "Cash on delivery order", "customer");
        }
        return order;
    }

    /** From the payment page: customer gives up on paying online and chooses cash on delivery. */
    @Transactional
    public void switchToCod(Long orderId, long customerId) {
        Order o = orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (o.getStatus() != OrderStatus.PAYMENT_PENDING) throw ApiException.badRequest("This order is already " + o.getStatus());
        if (!props.pricing().codEnabled() || o.getTotal().compareTo(props.pricing().codMaxOrderValue()) > 0) {
            throw ApiException.badRequest("Cash on delivery is not available for this order");
        }
        if (!shipping.check(o.getShipPincode(), 200, true).codAvailable()) {
            throw ApiException.badRequest("Cash on delivery is not available for pincode " + o.getShipPincode());
        }
        o.switchToCod();
        confirm(o.getId(), "Customer chose cash on delivery", "customer");
    }

    public Order get(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    /**
     * Payment succeeded (or COD accepted). Idempotent. Reduces stock and triggers fulfilment
     * (invoice, shipment, notifications) after the transaction commits.
     */
    @Transactional
    public void confirm(Long orderId, String note, String actor) {
        Order o = get(orderId);
        if (o.getStatus() != OrderStatus.PAYMENT_PENDING) return;
        o.moveTo(OrderStatus.CONFIRMED, note, actor);
        for (OrderItem i : o.getItems()) {
            if (products.decrementStock(i.getSku(), i.getQty()) == 0) {
                o.note("Stock was short for " + i.getSku() + " when payment arrived; please check", "system");
            }
        }
        events.publishEvent(new OrderConfirmedEvent(o.getId()));
    }

    /** Owner moves the order along. READY books the courier (BRD FR-27). */
    @Transactional
    public void changeStatus(Long orderId, OrderStatus next, String note, String actor) {
        Order o = get(orderId);
        if (o.getStatus() == next) return;
        if (!o.getStatus().canMoveTo(next)) throw ApiException.badRequest("Can't move from " + o.getStatus() + " to " + next);
        if (next == OrderStatus.READY) {
            Shipment s = shipping.book(orderId);
            note = (note == null || note.isBlank() ? "" : note + " · ") + "Courier booked: " + s.getCourier() + " AWB " + s.getAwb();
        }
        o.moveTo(next, note, actor);
        events.publishEvent(new OrderStatusChangedEvent(o.getId(), next));
    }

    @Transactional(readOnly = true)
    public List<OrderView.Summary> listForCustomer(long customerId) {
        return orders.findByCustomerIdOrderByIdDesc(customerId).stream().map(o -> summary(o, null)).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderView.Summary> listForAdmin(OrderStatus status) {
        List<Order> list = status == null ? orders.findTop200ByOrderByIdDesc() : orders.findTop200ByStatusOrderByIdDesc(status);
        Map<Long, Customer> cs = customers.findAllById(list.stream().map(Order::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        return list.stream().map(o -> summary(o, cs.get(o.getCustomerId()))).toList();
    }

    @Transactional(readOnly = true)
    public OrderView view(Long orderId, boolean admin) {
        Order o = get(orderId);
        Map<String, Product> bySku = products.findAllById(o.getItems().stream().map(OrderItem::getSku).toList())
                .stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        List<OrderView.Item> items = o.getItems().stream().map(i -> {
            Product p = bySku.get(i.getSku());
            String image = p == null || p.getImages().isEmpty() ? null : storage.publicUrl(p.getImages().get(0).getStorageKey());
            return new OrderView.Item(i.getSku(), i.getName(), p == null ? null : p.getSlug(), image, i.getUnitPrice(), i.getMrp(),
                    i.getQty(), i.lineTotal(), i.customisation());
        }).toList();
        List<OrderView.Event> evs = o.getEvents().stream()
                .map(e -> new OrderView.Event(e.getStatus(), e.getNote(), admin ? e.getActor() : null, e.getCreatedAt())).toList();
        OrderView.ShipmentInfo ship = shipping.forOrder(o.getId())
                .map(s -> new OrderView.ShipmentInfo(s.getStatus(), s.getCourier(), s.getAwb(), s.getTrackingUrl(),
                        admin ? s.getLabelUrl() : null, admin ? s.getLastError() : null))
                .orElse(null);
        String invoiceNo = invoices.forOrder(o.getId()).map(inv -> inv.getInvoiceNo()).orElse(null);
        OrderView.Contact contact = null;
        if (admin) {
            Customer c = customers.findById(o.getCustomerId()).orElse(null);
            if (c != null) contact = new OrderView.Contact(c.getName(), c.getEmail(), c.getPhone());
        }
        return new OrderView(o.getId(), o.getOrderNo(), o.getStatus(), o.getPaymentMode(), o.getCreatedAt(), o.address(),
                items, o.getSubtotal(), o.getShippingFee(), o.getDiscount(), o.getTotal(), evs, ship, invoiceNo,
                o.getStatus() == OrderStatus.PAYMENT_PENDING && o.getPaymentMode() == PaymentMode.ONLINE,
                contact, admin ? o.getStatus().next() : null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s.name(), orders.countByStatus(s));
        m.put("byStatus", byStatus);
        var since = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        List<Order> today = orders.findByCreatedAtAfter(since).stream().filter(o -> o.getStatus() != OrderStatus.PAYMENT_PENDING
                && o.getStatus() != OrderStatus.CANCELLED).toList();
        m.put("ordersToday", today.size());
        m.put("revenueToday", today.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        return m;
    }

    private OrderView.Summary summary(Order o, Customer c) {
        boolean customised = o.getItems().stream().anyMatch(i -> i.customisation() != null);
        return new OrderView.Summary(o.getId(), o.getOrderNo(), o.getStatus(), o.getPaymentMode(), o.getTotal(), o.getCreatedAt(),
                o.getItems().stream().mapToInt(OrderItem::getQty).sum(),
                o.getItems().isEmpty() ? null : o.getItems().get(0).getName(),
                c == null ? null : c.getName(), o.address().city(), customised);
    }

    public boolean isOwner(Long orderId, long customerId) {
        return orders.findByIdAndCustomerId(orderId, customerId).isPresent();
    }
}
