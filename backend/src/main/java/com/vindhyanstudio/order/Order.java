package com.vindhyanstudio.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.vindhyanstudio.common.Json;

import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderNo;
    private Long customerId;
    private String addressJson;
    private String shipPincode;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal total;
    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("createdAt ASC")
    private List<OrderEvent> events = new ArrayList<>();

    protected Order() {}

    public Order(Long customerId, AddressSnapshot address, PaymentMode mode) {
        this.customerId = customerId;
        this.addressJson = Json.write(address);
        this.shipPincode = address.pincode();
        this.paymentMode = mode;
        this.status = OrderStatus.PAYMENT_PENDING;
        this.events.add(new OrderEvent(OrderStatus.PAYMENT_PENDING, "Order placed", "customer"));
    }

    /** Records a status change on the timeline. Callers check {@link OrderStatus#canMoveTo} first. */
    public void moveTo(OrderStatus next, String note, String actor) {
        this.status = next;
        this.updatedAt = OffsetDateTime.now();
        this.events.add(new OrderEvent(next, note, actor));
    }

    void switchToCod() {
        this.paymentMode = PaymentMode.COD;
    }

    public void note(String note, String actor) {
        this.events.add(new OrderEvent(status, note, actor));
    }

    public AddressSnapshot address() {
        return Json.read(addressJson, AddressSnapshot.class);
    }

    public int totalWeightG(java.util.function.ToIntFunction<OrderItem> weightOf) {
        return items.stream().mapToInt(i -> weightOf.applyAsInt(i) * i.getQty()).sum();
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getCustomerId() { return customerId; }
    public String getShipPincode() { return shipPincode; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public OrderStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
    public List<OrderEvent> getEvents() { return events; }
}
