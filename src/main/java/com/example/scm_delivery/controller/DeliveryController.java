package com.example.scm_delivery.controller;

import com.example.scm_delivery.domain.Delivery;
import com.example.scm_delivery.domain.LocationLog;
import com.example.scm_delivery.domain.Pod;
import com.example.scm_delivery.dto.DeliveryResponseDto;
import com.example.scm_delivery.dto.DeliveryWebhookRequestDto;
import com.example.scm_delivery.dto.LocationDto;
import com.example.scm_delivery.dto.ShipmentRequestDto;
import com.example.scm_delivery.dto.ShipmentResponseDto;
import com.example.scm_delivery.dto.SimulationRequestDto;
import com.example.scm_delivery.service.DeliveryService;
import com.example.scm_delivery.service.DeliverySimulationService;
import com.example.scm_delivery.service.S3Service;
import com.example.scm_delivery.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Delivery Carrier API", description = "Delivery Carrier (DLV) 관제 및 배송 관리 API")
@RestController
@RequestMapping("/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final S3Service s3Service;
    private final TrackingService trackingService; // Redis 기반 실시간 위치 서비스 주입
    private final DeliverySimulationService deliverySimulationService; // 시뮬레이션 서비스 주입

    // 0. [내부 관제용] 출고 전 운송장 번호 사전 발급 API
    @Operation(summary = "운송장 번호 채번/발급", description = "수령인, 배송지, 상품 정보를 넘겨주면 운송장 번호를 사전 채번하여 응답합니다.")
    @PostMapping("/issue-tracking-number")
    public ResponseEntity<ShipmentResponseDto> issueTrackingNumber(
            @RequestParam(required = false, defaultValue = "CJ") String courierCode,
            @RequestBody ShipmentRequestDto requestDto) {

        // 1. 운송장 번호 생성
        String trackingNumber = deliveryService.generateTrackingNumber(courierCode);

        // 2. (선택) 수신받은 수령인/주소/상품 정보를 DB에 저장하거나 초기 배송 건으로 등록하는 로직
        // deliveryService.registerShipmentInfo(trackingNumber, requestDto);

        // 3. 응답 DTO 생성
        ShipmentResponseDto response = new ShipmentResponseDto(
                "SUCCESS",
                courierCode.toUpperCase(),
                trackingNumber,
                LocalDateTime.now().toString()
        );

        return ResponseEntity.ok(response);
    }

    // 1. 외부 택배사 배송 완료 상태 수신 Inbound Webhook
    @Operation(summary = "외부 택배사 배송 완료 Webhook 수신", description = "외부 택배사로부터 배송 완료 상태 및 POD 정보를 수신합니다.")
    @PostMapping("/webhooks/status")
    public ResponseEntity<String> receiveWebhookStatus(@RequestBody DeliveryWebhookRequestDto requestDto) {
        deliveryService.completeDeliveryWithPod(requestDto.getTrackingNumber(), requestDto.getProofImageUrl());
        return ResponseEntity.ok("SUCCESS");
    }

    // 2. 단건 배송 추적 조회
    @Operation(summary = "배송 추적 상태 조회", description = "송장 번호 기반으로 단건 배송 상태 및 POD 정보를 조회합니다.")
    @GetMapping("/trackings/{trackingNo}")
    public ResponseEntity<DeliveryResponseDto> getTrackingStatus(@PathVariable String trackingNo) {
        Delivery delivery = deliveryService.getDelivery(trackingNo);
        Pod pod = deliveryService.getPod(trackingNo);

        String proofImageUrl = (pod != null) ? pod.getS3ImageUrl() : null;

        // 🚀 DTO에 새로 만들어둔 from() 메서드 사용!
        DeliveryResponseDto response = DeliveryResponseDto.from(delivery, proofImageUrl);

        return ResponseEntity.ok(response);
    }

    // 3. POD(배송 완료 증빙) 이미지 업로드 API (방법 1: 실체 S3 업로드)
    @Operation(summary = "POD 증빙 사진/서명 업로드 (S3연동)", description = "기사님 PDA 앱 등에서 찍은 POD 이미지를 AWS S3에 업로드 후 배송 완료 처리합니다.")
    @PostMapping(value = "/{trackingNumber}/pod", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadPod(
            @PathVariable String trackingNumber,
            @RequestPart("file") MultipartFile file) {
        try {
            String podUrl = s3Service.uploadPodImage(file);
            deliveryService.completeDeliveryWithPod(trackingNumber, podUrl);
            return ResponseEntity.ok("배송 완료 및 POD 등록 성공: " + podUrl);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("S3 업로드 실패: " + e.getMessage());
        }
    }

    // 🚀 3-1. (방법 2: 테스트용) S3 업로드 없이 가짜 URL로 즉시 배송 완료 및 카프카 이벤트 발행
    @Operation(summary = "테스트용 배송 완료 처리 (S3 우회)", description = "AWS S3 연동 없이 더미 POD 이미지 URL로 즉시 배송 완료 처리하고 카프카 종결 이벤트를 발행합니다.")
    @PostMapping("/test/{trackingNumber}/complete")
    public ResponseEntity<Pod> completeTestDelivery(@PathVariable String trackingNumber) {
        String dummyS3Url = "https://scm-delivery-pod-bucket.s3.ap-northeast-2.amazonaws.com/pod/sample-proof.jpg";
        Pod pod = deliveryService.completeDeliveryWithPod(trackingNumber, dummyS3Url);
        return ResponseEntity.ok(pod);
    }

    // --- [관제 대시보드 및 내부 관제용 API] ---

    // 4. 전체 배송 목록 조회
    @Operation(summary = "전체 배송 목록 조회")
    @GetMapping("/deliveries")
    public ResponseEntity<List<Delivery>> getAllDeliveries() {
        return ResponseEntity.ok(deliveryService.getAllDeliveries());
    }

    // 5-1. [DB 저장용] 차량/화물 실시간 위치 수신
    @Operation(summary = "차량/화물 실시간 위치 수신 (PostgreSQL 저장)", description = "PDA 및 GPS 단말기로부터 실시간 위경도 데이터 수신 후 DB 저장")
    @PostMapping("/tracking/location")
    public ResponseEntity<LocationLog> saveLocation(@RequestParam String deliveryId,
                                                    @RequestParam BigDecimal latitude,
                                                    @RequestParam BigDecimal longitude) {
        return ResponseEntity.ok(deliveryService.saveLocation(deliveryId, latitude, longitude));
    }

    // 5-2. [Redis 저장용] 실시간 위치 정보 수신
    @Operation(summary = "Redis 실시간 위치 정보 저장", description = "GPS 단말기로부터 받은 실시간 위치 데이터를 Redis에 빠른 캐싱 저장합니다.")
    @PostMapping("/redis/location")
    public ResponseEntity<String> saveRedisLocation(@RequestBody LocationDto locationDto) {
        trackingService.saveLocation(locationDto);
        return ResponseEntity.ok("위치 정보가 Redis에 성공적으로 저장되었습니다.");
    }

    // 5-3. [Redis 조회용] 실시간 위치 정보 조회
    @Operation(summary = "Redis 실시간 위치 정보 조회", description = "Redis에 캐싱된 최신 배송 위치 데이터를 조회합니다.")
    @GetMapping("/redis/location/{deliveryId}")
    public ResponseEntity<LocationDto> getRedisLocation(@PathVariable String deliveryId) {
        LocationDto location = trackingService.getLocation(deliveryId);
        if (location == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(location);
    }

    // 6. 배송 위치 로그 이력 조회
    @Operation(summary = "배송 위치 로그 이력 조회")
    @GetMapping("/deliveries/{deliveryId}/logs")
    public ResponseEntity<List<LocationLog>> getLocationLogs(@PathVariable String deliveryId) {
        return ResponseEntity.ok(deliveryService.getLocationLogs(deliveryId));
    }

    // 7. [시뮬레이션 전용] 실시간 도로 이동 시작 API
    @Operation(summary = "배달 실시간 도로 이동 시뮬레이션 시작", description = "목적지 주소를 입력받아 택배 회사(종로)부터 목적지까지 실제 도로를 따라 3초 간격으로 실시간 이동합니다.")
    @PostMapping("/simulation/start")
    public ResponseEntity<String> startSimulation(@RequestBody SimulationRequestDto requestDto) {
        deliverySimulationService.runDeliverySimulation(
                requestDto.getDeliveryId(),
                requestDto.getDestinationAddress()
        );
        return ResponseEntity.ok("🚚 배달 시뮬레이션이 시작되었습니다! Grafana 대시보드(Auto-refresh 5s)에서 실시간 이동을 확인하세요.");
    }
}