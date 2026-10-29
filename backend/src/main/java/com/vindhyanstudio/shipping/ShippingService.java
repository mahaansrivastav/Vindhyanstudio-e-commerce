package com.vindhyanstudio.shipping;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vindhyanstudio.catalogue.Category;
import com.vindhyanstudio.catalogue.Product;
import com.vindhyanstudio.catalogue.ProductRepository;
import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.customer.CustomerRepository;
import com.vindhyanstudio.order.Order;
import com.vindhyanstudio.order.OrderItem;
import com.vindhyanstudio.order.OrderRepository;
import com.vindhyanstudio.order.PaymentMode;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private final ShippingProvider provider;
    private final ShipmentRepository shipments;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final OrderRepository orders;

    public ShippingService(ShippingProvider provider, ShipmentRepository shipments, ProductRepository products,
                           CustomerRepository customers, OrderRepository orders) {
        this.orders = orders;
        this.provider = provider;
        this.shipments = shipments;
        this.products = products;
        this.customers = customers;
    }

    public ShippingProvider.Serviceability check(String pincode, int weightG, boolean cod) {
        if (pincode == null || !pincode.matches("[1-9][0-9]{5}")) throw ApiException.badRequest("Enter a valid 6-digit pincode");
        try {
            return provider.check(pincode, weightG, cod);
        } catch (Exception e) {
            log.warn("Serviceability check failed for {}", pincode, e);
            // Don't block checkout if the courier API is down.
            return new ShippingProvider.Serviceability(true, null, false);
        }
    }

    /** Creates the provider order. Never throws: failures are stored on the shipment for the admin to retry. */
    @Transactional
    public Shipment create(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow();
        Shipment s = shipments.findByOrderId(order.getId()).orElseGet(() -> shipments.save(new Shipment(order.getId(), provider.name())));
        if (s.getShipmentId() != null) return s;
        try {
            s.created(provider.createOrder(request(order)));
        } catch (Exception e) {
            log.error("Shipment creation failed for {}", order.getOrderNo(), e);
            s.failed("CREATE_FAILED", e.getMessage());
        }
        return s;
    }

    /** Books the courier when the parcel is ready. Throws so the admin sees the error immediately. */
    @Transactional
    public Shipment book(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow();
        Shipment s = create(orderId);
        if (s.getShipmentId() == null) throw ApiException.badRequest("Shipment could not be created: " + s.getLastError());
        if (s.getAwb() != null || "BOOKED".equals(s.getStatus())) return s;
        try {
            s.booked(provider.book(s.getShipmentId()));
        } catch (Exception e) {
            log.error("Courier booking failed for {}", order.getOrderNo(), e);
            s.failed("BOOK_FAILED", e.getMessage());
            throw ApiException.badRequest("Courier booking failed: " + e.getMessage());
        }
        return s;
    }

    @Transactional
    public void providerStatus(Long orderId, String status) {
        shipments.findByOrderId(orderId).ifPresent(s -> s.providerStatus(status));
    }

    public java.util.Optional<Shipment> forOrder(Long orderId) {
        return shipments.findByOrderId(orderId);
    }

    public int weightG(Order order) {
        Map<String, Product> bySku = products.findAllById(order.getItems().stream().map(OrderItem::getSku).toList())
                .stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        return order.totalWeightG(i -> bySku.containsKey(i.getSku()) ? bySku.get(i.getSku()).getCategory().getWeightG() : 100);
    }

    private ShippingProvider.Request request(Order order) {
        Map<String, Product> bySku = products.findAllById(order.getItems().stream().map(OrderItem::getSku).toList())
                .stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        BigDecimal len = BigDecimal.ONE, wid = BigDecimal.ONE, hgt = BigDecimal.ZERO;
        for (OrderItem i : order.getItems()) {
            Product p = bySku.get(i.getSku());
            if (p == null) continue;
            Category c = p.getCategory();
            len = len.max(c.getLengthCm());
            wid = wid.max(c.getWidthCm());
            hgt = hgt.add(c.getHeightCm().multiply(BigDecimal.valueOf(i.getQty())));
        }
        String email = customers.findById(order.getCustomerId()).map(c -> c.getEmail()).orElse(null);
        return new ShippingProvider.Request(
                order.getOrderNo(),
                order.getCreatedAt().atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalDateTime(),
                order.address(), email,
                order.getItems().stream().map(i -> new ShippingProvider.Item(i.getName(), i.getSku(), i.getQty(), i.getUnitPrice(), i.getHsnCode())).toList(),
                order.getPaymentMode() == PaymentMode.COD,
                order.getSubtotal(), order.getShippingFee(), order.getTotal(),
                weightG(order) / 1000.0, len, wid, hgt.max(BigDecimal.ONE));
    }
}
