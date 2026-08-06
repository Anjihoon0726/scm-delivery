package com.example.scm_delivery.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", length = 50)
    private String aggregateType; // "DELIVERY"

    @Column(name = "aggregate_id", length = 50)
    private String aggregateId;   // orderId 또는 trackingNumber

    @Column(name = "event_type", length = 50)
    private String eventType;     // "delivery.started"

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;       // 이벤트 JSON 데이터

    @Column(name = "status", length = 20)
    private String status;        // "INIT", "PUBLISHED"

    @Column(name = "process_at")
    private LocalDateTime processAt; // 👈 1초 뒤 전송되도록 저장할 시간 기준점

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public OutboxEvent(String aggregateType, String aggregateId, String eventType,
                       String payload, String status, LocalDateTime processAt, LocalDateTime createdAt) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.processAt = processAt;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // 카프카 전송 성공 시 상태 업데이트용
    public void markAsPublished() {
        this.status = "PUBLISHED";
    }
}