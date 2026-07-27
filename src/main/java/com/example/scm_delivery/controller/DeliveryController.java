package com.example.scm_delivery.controller;

import com.example.scm_delivery.domain.Delivery;
import com.example.scm_delivery.domain.LocationLog;
import com.example.scm_delivery.domain.Pod;
import com.example.scm_delivery.dto.DeliveryResponseDto;
import com.example.scm_delivery.dto.DeliveryWebhookRequestDto;
import com.example.scm_delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Delivery Carrier API", description = "Delivery Carrier (DLV) 관제 및 배송 관리 API")
@RestController
@RequestMapping("/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    // 1. 외부 택배사 배송 완료 상태 수신 Inbound Webhook (POST /v1/delivery/webhooks/status)
    @Operation(summary = "외부 택배사 배송 완료 Webhook 수신", description = "외부 택배사로부터 배송 완료 상태 및 POD 정보를 수신합니다.")
    @PostMapping("/webhooks/status")
    public ResponseEntity<String> receiveWebhookStatus(@RequestBody DeliveryWebhookRequestDto requestDto) {
        deliveryService.completeDeliveryWithPod(requestDto.getTrackingNumber(), requestDto.getProofImageUrl());
        return ResponseEntity.ok("SUCCESS");
    }

    // 2. 단건 배송 추적 조회 (GET /v1/delivery/trackings/{trackingNo})
    @Operation(summary = "배송 추적 상태 조회", description = "송장 번호 기반으로 단건 배송 상태 및 POD 정보를 조회합니다.")
    @GetMapping("/trackings/{trackingNo}")
    public ResponseEntity<DeliveryResponseDto> getTrackingStatus(@PathVariable String trackingNo) {
        Delivery delivery = deliveryService.getDelivery(trackingNo);
        Pod pod = deliveryService.getPod(trackingNo);

        DeliveryResponseDto response = new DeliveryResponseDto(
                delivery.getOrderId(),
                delivery.getTrackingNumber(),
                delivery.getStatus(),
                delivery.getDeliveredAt() != null ? delivery.getDeliveredAt().toString() : null,
                pod != null ? pod.getS3ImageUrl() : null
        );
        return ResponseEntity.ok(response);
    }

    // --- [관제 대시보드 및 내부 관제용 API] ---

    // 3. 전체 배송 목록 조회 (GET /v1/delivery/deliveries)
    @Operation(summary = "전체 배송 목록 조회")
    @GetMapping("/deliveries")
    public ResponseEntity<List<Delivery>> getAllDeliveries() {
        return ResponseEntity.ok(deliveryService.getAllDeliveries());
    }

    // 4. 차량/화물 실시간 위치 수신 (POST /v1/delivery/tracking/location)
    @Operation(summary = "차량/화물 실시간 위치 수신", description = "PDA 및 GPS 단말기로부터 실시간 위경도 데이터 수신")
    @PostMapping("/tracking/location")
    public ResponseEntity<LocationLog> saveLocation(@RequestParam String deliveryId,
                                                    @RequestParam BigDecimal latitude,
                                                    @RequestParam BigDecimal longitude) {
        return ResponseEntity.ok(deliveryService.saveLocation(deliveryId, latitude, longitude));
    }

    // 5. 배송 위치 로그 이력 조회 (GET /v1/delivery/deliveries/{deliveryId}/logs)
    @Operation(summary = "배송 위치 로그 이력 조회")
    @GetMapping("/deliveries/{deliveryId}/logs")
    public ResponseEntity<List<LocationLog>> getLocationLogs(@PathVariable String deliveryId) {
        return ResponseEntity.ok(deliveryService.getLocationLogs(deliveryId));
    }
}
