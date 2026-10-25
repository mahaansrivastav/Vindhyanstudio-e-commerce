package com.vindhyanstudio.shipping;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private String provider;
    private String providerOrderId;
    private String shipmentId;
    private String awb;
    private String courier;
    private String labelUrl;
    private String trackingUrl;
    /** CREATE_FAILED, CREATED, BOOK_FAILED, BOOKED, then the provider's own status text. */
    private String status;
    private String lastError;
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Shipment() {}

    Shipment(Long orderId, String provider) {
        this.orderId = orderId;
        this.provider = provider;
        this.status = "PENDING";
    }

    void created(ShippingProvider.Created c) {
        providerOrderId = c.providerOrderId();
        shipmentId = c.shipmentId();
        status = "CREATED";
        lastError = null;
        touch();
    }

    void booked(ShippingProvider.Booked b) {
        awb = b.awb();
        courier = b.courier();
        labelUrl = b.labelUrl();
        trackingUrl = b.trackingUrl();
        status = "BOOKED";
        lastError = null;
        touch();
    }

    void failed(String status, String error) {
        this.status = status;
        this.lastError = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 1000));
        touch();
    }

    void providerStatus(String s) {
        this.status = s;
        touch();
    }

    private void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getShipmentId() { return shipmentId; }
    public String getAwb() { return awb; }
    public String getCourier() { return courier; }
    public String getLabelUrl() { return labelUrl; }
    public String getTrackingUrl() { return trackingUrl; }
    public String getStatus() { return status; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
