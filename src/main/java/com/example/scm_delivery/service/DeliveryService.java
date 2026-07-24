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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final LocationLogRepository locationLogRepository;
    private final PodRepository podRepository;

    // Kafka 연동을 위한 Template 주입 (추가)
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 1. 배송 등록 (POST /api/waybills 수신건)
    @Transactional
    public Delivery createDelivery(String deliveryId, String outboundId) {
        Delivery delivery = new Delivery(deliveryId, outboundId);
        return deliveryRepository.save(delivery);
    }

    // 2. 전체 배송 목록 조회
    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    // 3. 단건 배송 조회
    public Delivery getDelivery(String deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new DeliveryException("해당 배송건이 존재하지 않습니다. id=" + deliveryId));
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

    // 6. 인도 증빙(POD) 등록 및 배송 완료 처리 (+ Kafka 이벤트 발행 추가)
    @Transactional
    public Pod completeDeliveryWithPod(String deliveryId, String s3ImageUrl) {
        // 배송건 존재 확인
        Delivery delivery = getDelivery(deliveryId);

        // 배송 상태를 완료(COMPLETED)로 변경
        delivery.complete();

        // POD 증빙 생성 및 저장
        Pod pod = new Pod(deliveryId, s3ImageUrl);
        Pod savedPod = podRepository.save(pod);

        // 🌟 [추가] Kafka(MSK)로 배송완료 이벤트 발행 -> Notice(Slack) 모듈로 전달
        kafkaTemplate.send("delivery-events", "DELIVERY_COMPLETED:" + deliveryId);

        return savedPod;
    }

    // 7. 인도 증빙 조회
    public Pod getPod(String deliveryId) {
        return podRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryException("해당 배송건의 인도 증빙 정보가 없습니다. id=" + deliveryId));
    }
}