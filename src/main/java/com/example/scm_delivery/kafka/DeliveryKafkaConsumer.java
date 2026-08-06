package com.example.scm_delivery.kafka;

import com.example.scm_delivery.dto.OutboundDispatchedEvent;
import com.example.scm_delivery.service.DeliveryService;
import com.example.scm_delivery.service.DeliverySimulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryKafkaConsumer {

    private final DeliveryService deliveryService;
    private final DeliverySimulationService deliverySimulationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "fulfillment.outbound.dispatched", groupId = "delivery-group")
    public void consumeOutboundEvent(String message) {
        try {
            OutboundDispatchedEvent event = objectMapper.readValue(message, OutboundDispatchedEvent.class);

            // 이벤트 추적을 위해 eventId, traceId, correlationId를 로그에 함께 기록
            log.info("📩 [Kafka] 출고 완료 이벤트 수신 - eventId: {}, traceId: {}, correlationId: {}, trackingNo: {}",
                    event.getEventId(), event.getTraceId(), event.getCorrelationId(), event.getTrackingNumber());

            // 1. 배송 등록 및 배송 시작 카프카 이벤트 발행
            deliveryService.createDeliveryFromEvent(event);

            // 2. 목적지 주소 검증 (null/빈값 일 경우 기본값 처리)
            String destinationAddress = (event.getDestinationAddress() != null && !event.getDestinationAddress().isBlank())
                    ? event.getDestinationAddress()
                    : "서울특별시 종로구 세종대로 178";

            // 3. 실시간 위치 시뮬레이션 호출
            deliverySimulationService.runDeliverySimulation(
                    event.getTrackingNumber(),
                    destinationAddress
            );

        } catch (Exception e) {
            log.error("❌ 카프카 메시지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}