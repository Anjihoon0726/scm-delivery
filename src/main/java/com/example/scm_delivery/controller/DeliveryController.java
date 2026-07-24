package com.example.scm_delivery.controller;

import com.example.scm_delivery.domain.Delivery;
import com.example.scm_delivery.domain.LocationLog;
import com.example.scm_delivery.domain.Pod;
import com.example.scm_delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "택배 관제 API", description = "물류 출고 수신, 실시간 위치 관제 및 POD 배송 완료 API")
@RestController
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    // 1. 물류센터 출고 요청 및 운송장 수신 (POST /api/waybills)
    @Operation(summary = "1. 물류 출고 수신 및 운송장 생성", description = "물류(WMS) 시스템으로부터 출고/배송 시작 데이터 수신")
    @PostMapping("/api/waybills")
    public Delivery createDelivery(@RequestParam String deliveryId, @RequestParam String outboundId) {
        return deliveryService.createDelivery(deliveryId, outboundId);
    }

    // 2. 전체 배송 목록 조회 (GET /api/deliveries)
    @Operation(summary = "2. 전체 배송 목록 조회")
    @GetMapping("/api/deliveries")
    public List<Delivery> getAllDeliveries() {
        return deliveryService.getAllDeliveries();
    }

    // 3. 단건 배송 조회 (GET /api/deliveries/{deliveryId})
    @Operation(summary = "3. 단건 배송 정보 조회")
    @GetMapping("/api/deliveries/{deliveryId}")
    public Delivery getDelivery(@PathVariable String deliveryId) {
        return deliveryService.getDelivery(deliveryId);
    }

    // 4. 위치 수신 (POST /api/deliveries/tracking)
    @Operation(summary = "4. 차량/화물 실시간 위치 수신", description = "PDA 및 GPS 단말기로부터 실시간 위경도 데이터 수신")
    @PostMapping("/api/deliveries/tracking")
    public LocationLog saveLocation(@RequestParam String deliveryId,
                                    @RequestParam BigDecimal latitude,
                                    @RequestParam BigDecimal longitude) {
        return deliveryService.saveLocation(deliveryId, latitude, longitude);
    }

    // 4-1. 위치 로그 조회 (GET /api/deliveries/{deliveryId}/logs)
    @Operation(summary = "4-1. 배송 위치 로그 이력 조회")
    @GetMapping("/api/deliveries/{deliveryId}/logs")
    public List<LocationLog> getLocationLogs(@PathVariable String deliveryId) {
        return deliveryService.getLocationLogs(deliveryId);
    }

    // 5. 인도 증빙(POD) 등록 및 완료 처리 (POST /api/deliveries/pods)
    @Operation(summary = "5. 인도 증빙(POD) 등록 및 배송 완료", description = "POD 업로드 후 배송 완료 처리하며, Kafka 완료 이벤트를 발행합니다.")
    @PostMapping("/api/deliveries/pods")
    public Pod completeDelivery(@RequestParam String deliveryId, @RequestParam String s3ImageUrl) {
        return deliveryService.completeDeliveryWithPod(deliveryId, s3ImageUrl);
    }

    // 5-1. 인도 증빙 조회 (GET /api/deliveries/{deliveryId}/pod)
    @Operation(summary = "5-1. 인도 증빙(POD) 정보 조회")
    @GetMapping("/api/deliveries/{deliveryId}/pod")
    public Pod getPod(@PathVariable String deliveryId) {
        return deliveryService.getPod(deliveryId);
    }
}
