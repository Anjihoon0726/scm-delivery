package com.example.scm_delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackingDeliveredEvent {

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
    private String source; // ⭐️ @JsonProperty 추가

    // 2. EPCIS 2.0 및 배송 도메인 필드
    @JsonProperty("event_time")
    private String eventTime;

    @JsonProperty("action")
    @Builder.Default
    private String action = "OBSERVE";

    @JsonProperty("biz_step")
    @Builder.Default
    private String bizStep = "arriving"; // GS1 표준 값

    @JsonProperty("disposition")
    @Builder.Default
    private String disposition = "delivered"; // ⭐️ 배송 완료 규격값 (delivered)

    @JsonProperty("read_point")
    private String readPoint; // ⭐️ 추가

    @JsonProperty("biz_location")
    private String bizLocation; // ⭐️ 추가

    @JsonProperty("epc_list")
    private List<String> epcList; // ⭐️ 추가

    @JsonProperty("tracking_number")
    private String trackingNumber;

    @JsonProperty("order_id")
    private String orderId; // ⭐️ 추가

    @JsonProperty("pod_url")
    private String podUrl;
}