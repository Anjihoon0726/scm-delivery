package com.example.scm_delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackingStartedEvent {

    // 1. 공통 Header 6종 (schemaVersion: Integer)
    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("correlation_id")
    private String correlationId; // ⭐️ 추가

    @JsonProperty("schema_version")
    private Integer schemaVersion; // ⭐️ 추가 (Integer)

    @JsonProperty("occurred_at")
    private Long occurredAt;

    @JsonProperty("source")
    private String source; // ⭐️ @JsonProperty 누락 수정

    // 2. EPCIS 2.0 및 배송 도메인 필드
    @JsonProperty("event_time")
    private String eventTime;

    @JsonProperty("action") // ⭐️ @JsonProperty 누락 수정
    @Builder.Default
    private String action = "OBSERVE";

    @JsonProperty("biz_step")
    @Builder.Default
    private String bizStep = "shipping"; // 표준 규격값 (shipping)

    @JsonProperty("disposition") // ⭐️ @JsonProperty 누락 수정
    @Builder.Default
    private String disposition = "in_transit";

    @JsonProperty("read_point")
    private String readPoint; // ⭐️ 추가

    @JsonProperty("biz_location")
    private String bizLocation; // ⭐️ 추가

    @JsonProperty("epc_list")
    private List<String> epcList; // ⭐️ 추가

    @JsonProperty("tracking_number")
    private String trackingNumber;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("courier_code")
    private String courierCode;
}