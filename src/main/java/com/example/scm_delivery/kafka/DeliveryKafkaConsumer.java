package com.example.scm_delivery.kafka;

import com.example.scm_delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryKafkaConsumer {

    private final DeliveryService deliveryService;

    /**
     * 물류(FUL) 모듈에서 출고(DISPATCHED) 이벤트 수신 시 자동으로 배송 건 및 운송장 생성
     * 메시지 포맷 예시: "OUTBOUND_DISPATCHED:orderId:outboundId:courierCode"
     */
    @KafkaListener(topics = "fulfillment.outbound.dispatched", groupId = "delivery-group")
    public void consumeOutboundEvent(String message) {
        log.info("Received Outbound Event from Kafka: {}", message);

        try {
            if (message != null && message.startsWith("OUTBOUND_DISPATCHED:")) {
                String[] parts = message.split(":");
                if (parts.length >= 4) {
                    String orderId = parts[1];
                    String outboundId = parts[2];
                    String courierCode = parts[3];

                    // 자동으로 배송 등록 (DLV-택배사코드-NNNNN 송장번호 생성)
                    deliveryService.createDelivery(orderId, outboundId, courierCode);
                    log.info("Successfully created Delivery for outboundId: {}, courierCode: {}", outboundId, courierCode);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Outbound Event message: {}", message, e);
        }
    }
}