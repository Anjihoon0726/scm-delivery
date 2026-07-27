package com.example.scm_delivery.service;

import com.example.scm_delivery.domain.Delivery;
import com.example.scm_delivery.domain.LocationLog;
import com.example.scm_delivery.domain.Pod;
import com.example.scm_delivery.exception.DeliveryException;
import com.example.scm_delivery.repository.DeliveryRepository;
import com.example.scm_delivery.repository.LocationLogRepository;
import com.example.scm_delivery.repository.PodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final LocationLogRepository locationLogRepository;
    private final PodRepository podRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 1. 배송 등록 및 송장 번호 규칙 적용 (DLV-택배사코드-NNNNN)
    @Transactional
    public Delivery createDelivery(String orderId, String outboundId, String courierCode) {
        String deliveryId = "DLV-" + UUID.randomUUID().toString().substring(0, 8);

        // 🌟 [정의서 규격] DLV-택배사코드-NNNNN 규칙 적용 (예: DLV-CJ-99211)
        int randomNumber = (int) (Math.random() * 90000) + 10000;
        String trackingNumber = "DLV-" + (courierCode != null ? courierCode.toUpperCase() : "CJ") + "-" + randomNumber;

        Delivery delivery = new Delivery(deliveryId, orderId, trackingNumber, outboundId);
        return deliveryRepository.save(delivery);
    }

    // 2. 전체 배송 목록 조회
    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    // 3. 송장 번호 또는 deliveryId 기반 단건 배송 조회
    public Delivery getDelivery(String trackingNoOrId) {
        return deliveryRepository.findByTrackingNumber(trackingNoOrId)
                .orElseGet(() -> deliveryRepository.findById(trackingNoOrId)
                        .orElseThrow(() -> new DeliveryException("해당 배송건이 존재하지 않습니다. identifier=" + trackingNoOrId)));
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

    // 6. 인도 증빙(POD) 등록 및 배송 완료 처리 (송장 번호 기반)
    @Transactional
    public Pod completeDeliveryWithPod(String trackingNumber, String s3ImageUrl) {
        Delivery delivery = getDelivery(trackingNumber);

        // 배송 상태 완료(DELIVERED) 변경 및 시간 기록
        delivery.complete();

        // POD 증빙 생성 및 저장
        Pod pod = new Pod(delivery.getDeliveryId(), s3ImageUrl);
        Pod savedPod = podRepository.save(pod);

        // Kafka 배송완료 이벤트 발행
        kafkaTemplate.send("delivery-events", "DELIVERY_COMPLETED:" + delivery.getDeliveryId());

        return savedPod;
    }

    // 7. 인도 증빙 조회
    public Pod getPod(String trackingNoOrId) {
        Delivery delivery = getDelivery(trackingNoOrId);
        return podRepository.findByDeliveryId(delivery.getDeliveryId())
                .orElse(null);
    }
}