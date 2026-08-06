package com.example.scm_delivery.service;

import com.example.scm_delivery.domain.Delivery;
import com.example.scm_delivery.domain.LocationLog;
import com.example.scm_delivery.domain.OutboxEvent;
import com.example.scm_delivery.domain.Pod;
import com.example.scm_delivery.dto.OutboundDispatchedEvent;
import com.example.scm_delivery.dto.ShipmentRequestDto;
import com.example.scm_delivery.dto.TrackingDeliveredEvent;
import com.example.scm_delivery.dto.TrackingStartedEvent;
import com.example.scm_delivery.exception.DeliveryException;
import com.example.scm_delivery.kafka.DeliveryKafkaProducer;
import com.example.scm_delivery.repository.DeliveryRepository;
import com.example.scm_delivery.repository.LocationLogRepository;
import com.example.scm_delivery.repository.OutboxRepository;
import com.example.scm_delivery.repository.PodRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final LocationLogRepository locationLogRepository;
    private final PodRepository podRepository;
    private final DeliveryKafkaProducer deliveryKafkaProducer;
    private final OutboxRepository outboxRepository; // 👈 Outbox 전용 Repository
    private final ObjectMapper objectMapper;          // 👈 JSON 변환용

    // 📦 application.yml에서 출발지(창고/쇼핑몰) 고정 정보 로드
    @Value("${sender.name:hc_scm}")
    private String senderName;

    @Value("${sender.phone:02-1234-5678}")
    private String senderPhone;

    @Value("${sender.address:경기도 안산시 단원구 원곡동 828-6}")
    private String senderAddress;

    // 1-1. [API 호출용] 운송장 번호 채번
    public String generateTrackingNumber(String courierCode) {
        int randomNumber = (int) (Math.random() * 90000) + 10000;
        return "DLV-" + (courierCode != null && !courierCode.isBlank() ? courierCode.toUpperCase() : "CJ") + "-" + randomNumber;
    }

    // 1-2. 물류회사 API 요청 기반 배송 등록 및 delivery.tracking.started Outbox 저장
    @Transactional
    public Delivery registerShipment(ShipmentRequestDto requestDto, String courierCode) {
        String effectiveCourierCode = (requestDto.getCourierCode() != null && !requestDto.getCourierCode().isBlank())
                ? requestDto.getCourierCode() : courierCode;

        String trackingNumber = generateTrackingNumber(effectiveCourierCode);
        String deliveryId = trackingNumber;

        // items 추출
        String itemName = (requestDto.getItems() != null && !requestDto.getItems().isEmpty())
                ? requestDto.getItems().get(0).getProductId() : "상품";
        int totalQuantity = (requestDto.getItems() != null)
                ? requestDto.getItems().stream().mapToInt(ShipmentRequestDto.Item::getQuantity).sum() : 1;

        // box_packing 계산
        int boxQuantity = 1;
        String boxSize = "MEDIUM";
        if (requestDto.getBoxPacking() != null) {
            ShipmentRequestDto.BoxPacking box = requestDto.getBoxPacking();
            boxQuantity = box.getLarge() + box.getMedium() + box.getSmall();
            if (box.getLarge() > 0) boxSize = "LARGE";
            else if (box.getMedium() > 0) boxSize = "MEDIUM";
            else if (box.getSmall() > 0) boxSize = "SMALL";
        }

        ShipmentRequestDto.Destination dest = requestDto.getDestination();

        // 📞 전화번호가 비어있거나 넘어오지 않는 경우 고정값 01012345678 사용
        String phone = (dest != null && dest.getRecipientPhone() != null && !dest.getRecipientPhone().isBlank())
                ? dest.getRecipientPhone() : "01012345678";

        Delivery delivery = Delivery.builder()
                .deliveryId(deliveryId)
                .trackingNumber(trackingNumber)
                .orderId(requestDto.getOrderId())
                .courierCode(effectiveCourierCode != null ? effectiveCourierCode.toUpperCase() : "CJ")
                // 📦 [추가] 보내는 사람(출발지) 정보 세팅
                .senderName(senderName)
                .senderPhone(senderPhone)
                .senderAddress(senderAddress)
                .recipientName(dest != null && dest.getRecipientName() != null ? dest.getRecipientName() : "고객")
                .recipientPhone(phone)
                .zipCode(dest != null ? dest.getZipCode() : null)
                .address(dest != null && !dest.getRoadAddress().isBlank() ? dest.getRoadAddress() : "주소 미지정")
                .detailAddress(dest != null ? dest.getDetailAddress() : null)
                .deliveryRequest(dest != null ? dest.getDeliveryRequest() : null)
                .itemName(itemName)
                .quantity(totalQuantity)
                .boxQuantity(boxQuantity > 0 ? boxQuantity : 1)
                .boxSize(boxSize)
                .status("IN_TRANSIT")
                .build();

        Delivery savedDelivery = deliveryRepository.save(delivery);

        // Kafka 배송 시작 이벤트 객체 생성
        TrackingStartedEvent startedEvent = TrackingStartedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .traceId("00-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "-01")
                .correlationId("corr-" + UUID.randomUUID().toString().substring(0, 8))
                .schemaVersion(1)
                .occurredAt(System.currentTimeMillis())
                .source("scm-delivery-service")
                .eventTime(OffsetDateTime.now().toString())
                .action("OBSERVE")
                .bizStep("shipping")
                .disposition("in_transit")
                .orderId(savedDelivery.getOrderId())
                .trackingNumber(trackingNumber)
                .courierCode(savedDelivery.getCourierCode())
                .build();

        // 🚀 [Outbox 저장] 1초 지연 발행 예약
        saveOutboxEvent("delivery.tracking.started", trackingNumber, startedEvent);

        return savedDelivery;
    }

    // 1-3. 카프카 출고 이벤트 기반 배송 등록 및 delivery.tracking.started Outbox 저장
    @Transactional
    public Delivery createDeliveryFromEvent(OutboundDispatchedEvent event) {
        String deliveryId = "DLV-" + UUID.randomUUID().toString().substring(0, 8);

        String trackingNumber = event.getTrackingNumber();
        if (trackingNumber == null || trackingNumber.isBlank()) {
            trackingNumber = generateTrackingNumber(event.getCourierCode());
        }

        // 🎯 물류 창고 이벤트에서 전달받은 실제 주문자의 목적지 주소 검증
        String destinationAddress = (event.getDestinationAddress() != null && !event.getDestinationAddress().isBlank())
                ? event.getDestinationAddress()
                : "주소 미지정";

        Delivery delivery = Delivery.builder()
                .deliveryId(deliveryId)
                .orderId(event.getOrderId())
                .trackingNumber(trackingNumber)
                .outboundId(event.getEventId() != null ? event.getEventId() : "OUTBOUND-" + UUID.randomUUID().toString().substring(0, 8))
                .courierCode(event.getCourierCode() != null && !event.getCourierCode().isBlank() ? event.getCourierCode().toUpperCase() : "CJ")
                // 📦 보내는 사람(출발지) 정보 세팅
                .senderName(senderName)
                .senderPhone(senderPhone)
                .senderAddress(senderAddress)
                // 🏠 주문자 실제 배송지 주소 저장
                .address(destinationAddress)
                .recipientName("고객")
                .recipientPhone("01012345678")
                .itemName("출고 상품")
                .quantity(1)
                .boxQuantity(1)
                .boxSize("MEDIUM")
                .status("READY")
                .build();

        Delivery savedDelivery = deliveryRepository.save(delivery);

        TrackingStartedEvent startedEvent = TrackingStartedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .traceId(event.getTraceId() != null ? event.getTraceId() : "00-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "-01")
                .correlationId(event.getCorrelationId())
                .schemaVersion(1)
                .occurredAt(System.currentTimeMillis())
                .source("scm-delivery-service")
                .eventTime(OffsetDateTime.now().toString())
                .action("OBSERVE")
                .bizStep("shipping")
                .disposition("in_transit")
                .readPoint(event.getReadPoint())
                .bizLocation(event.getBizLocation())
                .epcList(event.getEpcList())
                .orderId(event.getOrderId())
                .trackingNumber(trackingNumber)
                .courierCode(savedDelivery.getCourierCode())
                .build();

        // 🚀 [Outbox 저장] 카프카 직접 발행 대신 1초 뒤 발행되도록 Outbox에 저장
        saveOutboxEvent("delivery.tracking.started", trackingNumber, startedEvent);

        return savedDelivery;
    }

    // 2. 전체 배송 목록 조회
    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    // 3. 단건 배송 조회
    public Delivery getDelivery(String trackingNoOrId) {
        return deliveryRepository.findById(trackingNoOrId)
                .orElseThrow(() -> new DeliveryException("해당 배송건이 존재하지 않습니다. identifier=" + trackingNoOrId));
    }

    // 4. 실시간 위치 기록 저장
    @Transactional
    public LocationLog saveLocation(String deliveryId, BigDecimal latitude, BigDecimal longitude) {
        getDelivery(deliveryId);
        LocationLog log = new LocationLog(deliveryId, latitude, longitude);
        return locationLogRepository.save(log);
    }

    // 5. 위치 기록 조회
    public List<LocationLog> getLocationLogs(String deliveryId) {
        return locationLogRepository.findByDeliveryIdOrderByLoggedAtDesc(deliveryId);
    }

    // 6. 인도 증빙(POD) 등록, 배송 완료 처리 및 카프카 이벤트 발행
    @Transactional
    public Pod completeDeliveryWithPod(String trackingNumber, String s3ImageUrl) {
        Delivery delivery = getDelivery(trackingNumber);

        delivery.complete();

        Pod pod = new Pod(delivery.getDeliveryId(), s3ImageUrl);
        Pod savedPod = podRepository.save(pod);

        TrackingDeliveredEvent deliveredEvent = TrackingDeliveredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .traceId("00-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "-01")
                .correlationId(null)
                .schemaVersion(1)
                .occurredAt(System.currentTimeMillis())
                .source("scm-delivery-service")
                .eventTime(OffsetDateTime.now().toString())
                .action("OBSERVE")
                .bizStep("arriving")
                .disposition("delivered")
                .trackingNumber(delivery.getTrackingNumber())
                .orderId(delivery.getOrderId())
                .podUrl(s3ImageUrl)
                .build();

        deliveryKafkaProducer.sendTrackingDelivered(deliveredEvent);

        return savedPod;
    }

    // 7. 인도 증빙 조회
    public Pod getPod(String trackingNoOrId) {
        Delivery delivery = getDelivery(trackingNoOrId);
        return podRepository.findByDeliveryId(delivery.getDeliveryId())
                .orElse(null);
    }

    // 🛠️ [공통 헬퍼 메서드] Outbox 테이블에 1초 지연 발행 예약 저장
    private void saveOutboxEvent(String topic, String aggregateId, Object eventObject) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String payloadJson = objectMapper.writeValueAsString(eventObject);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("DELIVERY")
                    .aggregateId(aggregateId)
                    .eventType(topic)
                    .payload(payloadJson)
                    .status("INIT")
                    .processAt(now.plusSeconds(1)) // 👈 현재 시간 기준 1초 뒤 설정
                    .createdAt(now)
                    .build();

            outboxRepository.save(outboxEvent);
            log.info("📦 [Outbox] 1초 지연 이벤트 저장 완료 - Topic: {}, Key: {}, ProcessAt: {}", topic, aggregateId, outboxEvent.getProcessAt());
        } catch (Exception e) {
            log.error("❌ Outbox 이벤트 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Outbox 이벤트 저장 중 오류 발생", e);
        }
    }
}