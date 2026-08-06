package com.example.scm_delivery.kafka;

import com.example.scm_delivery.dto.TrackingStartedEvent;
import com.example.scm_delivery.dto.TrackingDeliveredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // 🌟 1. 배송 시작 토픽 발행
    public void sendTrackingStarted(TrackingStartedEvent event) {
        try {
            String jsonValue = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("delivery.tracking.started", event.getTrackingNumber(), jsonValue);
            log.info("Published tracking.started event: {}", event.getTrackingNumber());
        } catch (Exception e) {
            log.error("Failed to send tracking.started event", e);
        }
    }

    // 🌟 2. 배송 완료 토픽 발행
    public void sendTrackingDelivered(TrackingDeliveredEvent event) {
        try {
            String jsonValue = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("delivery.tracking.delivered", event.getTrackingNumber(), jsonValue);
            log.info("Published tracking.delivered event: {}", event.getTrackingNumber());
        } catch (Exception e) {
            log.error("Failed to send tracking.delivered event", e);
        }
    }

    // 🚀 [신규 추가] OutboxScheduler에서 사용할 범용 카프카 전송 메서드
    public void send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload);
            log.info("🚀 [Outbox Kafka] Event Published - Topic: {}, Key: {}", topic, key);
        } catch (Exception e) {
            log.error("❌ [Outbox Kafka] Failed to send event - Topic: {}, Key: {}", topic, key, e);
            throw e; // Scheduler에서 실패를 감지할 수 있도록 예외를 던집니다.
        }
    }
}