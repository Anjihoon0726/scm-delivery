package com.example.scm_delivery.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @Column(name = "tracking_number", length = 50)
    private String trackingNumber; // PK (운송장 번호)

    @Column(name = "delivery_id", length = 50)
    private String deliveryId;

    @Column(name = "order_id", length = 50)
    private String orderId; // 주문번호 (COM-...)

    @Column(name = "outbound_id", length = 50)
    private String outboundId;

    @Column(name = "courier_code", length = 20)
    private String courierCode; // 택배사 코드 (CJ 등)

    @Column(name = "customer_code", length = 50)
    private String customerCode; // 물류회사 코드

    // 📦 [신규 추가] 보내는 사람(출발지) 정보 --
    @Column(name = "sender_name", length = 50)
    private String senderName;

    @Column(name = "sender_phone", length = 20)
    private String senderPhone;

    @Column(name = "sender_address", length = 255)
    private String senderAddress;

    // [신규 추가] 수령인 정보 --
    @Column(name = "recipient_name", length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    // [신규 추가] 배송지 정보 --
    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    // 🚀 [신규 추가] 배송 요청사항 (예: 문 앞에 놓아주세요)
    @Column(name = "delivery_request", length = 255)
    private String deliveryRequest;

    // [신규 추가] 상품 및 박스 정보 --
    @Column(name = "item_name", length = 100)
    private String itemName;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "box_quantity")
    private Integer boxQuantity;

    @Column(name = "box_size", length = 20)
    private String boxSize;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "READY";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt; // 배송완료 일시

    // 기존 생성자 유지
    public Delivery(String deliveryId, String orderId, String trackingNumber, String outboundId) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.trackingNumber = trackingNumber;
        this.outboundId = outboundId;
        this.status = "READY";
        this.createdAt = LocalDateTime.now();
    }

    // 배송 완료 시 상태 변경 및 완료 시간 기록
    public void complete() {
        this.status = "DELIVERED";
        this.deliveredAt = LocalDateTime.now();
    }
}