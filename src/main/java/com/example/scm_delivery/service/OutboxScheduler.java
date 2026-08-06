package com.example.scm_delivery.service;

import com.example.scm_delivery.domain.OutboxEvent;
import com.example.scm_delivery.kafka.DeliveryKafkaProducer;
import com.example.scm_delivery.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final DeliveryKafkaProducer kafkaProducer;

    // 0.2초마다 DB를 스캔하여 1초 지연 시간이 지난 이벤트 발행
    @Scheduled(fixedDelay = 200)
    @Transactional
    public void processOutboxEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = outboxRepository.findByStatusAndProcessAtLessThanEqual("INIT", now);

        for (OutboxEvent event : events) {
            // 카프카로 메시지 전송 (기존 Producer 메서드 활용)
            kafkaProducer.send(event.getEventType(), event.getAggregateId(), event.getPayload());

            // 상태를 PUBLISHED로 변경
            event.markAsPublished();
            log.info("1초 지연 후 Outbox 카프카 발행 완료: id={}", event.getAggregateId());
        }
    }
}