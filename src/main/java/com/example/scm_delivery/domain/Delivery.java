package com.example.scm_delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
public class Delivery {

    @Id
    @Column(name = "delivery_id", length = 50)
    private String deliveryId;

    @Column(name = "order_id", length = 50)
    private String orderId; // [추가] 주문번호 (COM-...)

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber; // [추가] 운송장번호 (DLV-CJ-...)

    @Column(name = "outbound_id", nullable = false, length = 50)
    private String outboundId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "READY";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt; // [추가] 배송완료 일시

    public Delivery(String deliveryId, String orderId, String trackingNumber, String outboundId) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.trackingNumber = trackingNumber;
        this.outboundId = outboundId;
        this.status = "READY";
        this.createdAt = LocalDateTime.now();
    }

    // [수정] 배송 완료 시 상태 변경 및 완료 시간 기록
    public void complete() {
        this.status = "DELIVERED";
        this.deliveredAt = LocalDateTime.now();
    }
}