package com.example.scm_delivery.controller;

import com.example.scm_delivery.dto.ShipmentRequestDto;
import com.example.scm_delivery.dto.ShipmentResponseDto;
import com.example.scm_delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "External Partner API", description = "외부 물류 연동 전용 API")
@RestController
@RequestMapping("/api/deliveries") // 물류 회사 전용 기본 경로
@RequiredArgsConstructor
public class ShipmentPartnerController {

    private final DeliveryService deliveryService;

    // 상대방 요청: POST http://.../api/deliveries/shipments
    @Operation(summary = "외부 물류 연동 운송장 사전 발급", description = "물류 회사 규격에 맞춘 출고 운송장 발급 API")
    @PostMapping("/shipments")
    public ResponseEntity<ShipmentResponseDto> issueShipment(
            @RequestParam(required = false, defaultValue = "CJ") String courierCode,
            @RequestBody ShipmentRequestDto requestDto) {

        // 1. 운송장 번호 생성
        String trackingNumber = deliveryService.generateTrackingNumber(courierCode);

        // 2. 응답 DTO 생성
        ShipmentResponseDto response = new ShipmentResponseDto(
                "SUCCESS",
                courierCode.toUpperCase(),
                trackingNumber,
                LocalDateTime.now().toString()
        );

        return ResponseEntity.ok(response);
    }
}