package com.example.scm_delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OutboundDispatchedEvent {

    // 1. 공통 Header 6종 (schemaVersion: Integer)
    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("correlation_id")
    private String correlationId;

    @JsonProperty("schema_version")
    private Integer schemaVersion; // ⭐️ String -> Integer 수정

    @JsonProperty("occurred_at")
    private Long occurredAt;

    @JsonProperty("source")
    private String source;

    // 2. EPCIS 2.0 및 배송 도메인 필드
    @JsonProperty("event_time")
    private String eventTime;

    @Builder.Default
    @JsonProperty("action")
    private String action = "OBSERVE";

    @JsonProperty("biz_step")
    private String bizStep;

    @JsonProperty("disposition")
    private String disposition;

    @JsonProperty("read_point")
    private String readPoint; // ⭐️ 추가

    @JsonProperty("biz_location")
    private String bizLocation; // ⭐️ 추가

    @JsonProperty("epc_list")
    private List<String> epcList; // ⭐️ 추가

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("tracking_number")
    private String trackingNumber;

    @JsonProperty("courier_code")
    private String courierCode;

    @JsonProperty("destination_address")
    private String destinationAddress;
}