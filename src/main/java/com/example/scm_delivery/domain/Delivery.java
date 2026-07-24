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

    @Column(name = "outbound_id", nullable = false, length = 50)
    private String outboundId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "READY";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Delivery(String deliveryId, String outboundId) {
        this.deliveryId = deliveryId;
        this.outboundId = outboundId;
        this.status = "READY";
        this.createdAt = LocalDateTime.now();
    }

    //[추가] 배송 완료로 상태를 바꾸는 스위치 메서드
    public void complete() {
        this.status = "COMPLETED";
    }
}