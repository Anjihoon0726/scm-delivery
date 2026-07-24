package com.example.scm_delivery.controller;

import com.example.scm_delivery.domain.Delivery;
import com.example.scm_delivery.domain.LocationLog;
import com.example.scm_delivery.domain.Pod;
import com.example.scm_delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    // 1. 배송 등록 (POST)
    @PostMapping
    public Delivery createDelivery(@RequestParam String deliveryId, @RequestParam String outboundId) {
        return deliveryService.createDelivery(deliveryId, outboundId);
    }

    // 2. 전체 배송 목록 조회 (GET)
    @GetMapping
    public List<Delivery> getAllDeliveries() {
        return deliveryService.getAllDeliveries();
    }

    // 3. 단건 배송 조회 (GET)
    @GetMapping("/{deliveryId}")
    public Delivery getDelivery(@PathVariable String deliveryId) {
        return deliveryService.getDelivery(deliveryId);
    }

    // 4. 위치 수신 (POST)
    @PostMapping("/tracking")
    public LocationLog saveLocation(@RequestParam String deliveryId,
                                    @RequestParam BigDecimal latitude,
                                    @RequestParam BigDecimal longitude) {
        return deliveryService.saveLocation(deliveryId, latitude, longitude);
    }

    // 4-1. 위치 로그 조회 (GET)
    @GetMapping("/{deliveryId}/logs")
    public List<LocationLog> getLocationLogs(@PathVariable String deliveryId) {
        return deliveryService.getLocationLogs(deliveryId);
    }

    // 5. 인도 증빙(POD) 등록 및 완료 처리 (POST)
    @PostMapping("/pods")
    public Pod completeDelivery(@RequestParam String deliveryId, @RequestParam String s3ImageUrl) {
        return deliveryService.completeDeliveryWithPod(deliveryId, s3ImageUrl);
    }

    // 5-1. 인도 증빙 조회 (GET)
    @GetMapping("/{deliveryId}/pod")
    public Pod getPod(@PathVariable String deliveryId) {
        return deliveryService.getPod(deliveryId);
    }
}
