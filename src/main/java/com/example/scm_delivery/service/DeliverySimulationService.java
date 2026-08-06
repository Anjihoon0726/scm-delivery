package com.example.scm_delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverySimulationService {

    private final DeliveryService deliveryService;
    private final RestTemplate restTemplate = new RestTemplate();

    // 출발지: SCM 메인 물류창고 (종로구 관철동/종각역 부근)
    private static final double START_LAT = 37.5695;
    private static final double START_LNG = 126.9868;

    @Async // 백그라운드 스레드에서 실시간 이동 수행
    public void runDeliverySimulation(String deliveryId, String destinationAddress) {
        // 🎯 1. DB에 저장되어 있는 진짜 주소 다시 조회 (호출부에서 종로 주소를 잘못 넘겨주는 경우 대비)
        String actualAddress = destinationAddress;
        try {
            var delivery = deliveryService.getDelivery(deliveryId);
            if (delivery.getAddress() != null && !delivery.getAddress().isBlank() && !delivery.getAddress().equals("주소 미지정")) {
                actualAddress = delivery.getAddress();
            }
        } catch (Exception e) {
            log.warn("배송지 주소 재조회 실패, 전달받은 주소 사용: {}", destinationAddress);
        }

        log.info("🚚 [{}] 배달 시뮬레이션 시작! 출발지: (37.5695, 126.9868) -> 목적지: {}", deliveryId, actualAddress);

        // 2. 주소 -> 위경도 변환 (OpenStreetMap Nominatim 사용)
        double[] destCoords = getCoordsFromAddress(actualAddress);
        double endLat = destCoords[0];
        double endLng = destCoords[1];

        // 3. 출발지(종로 창고) -> 목적지 간 실제 도로 경로 좌표 목록 받아오기 (OSRM Routing API)
        List<double[]> waypoints = getRouteWaypoints(START_LAT, START_LNG, endLat, endLng);
        log.info("🗺️ 총 {}개의 이동 경로 지점 생성 완료", waypoints.size());

        // 4. 3초 간격으로 DB에 좌표 저장 (실시간 배달 이동 연출)
        for (int i = 0; i < waypoints.size(); i++) {
            double[] point = waypoints.get(i);
            double lat = point[0];
            double lng = point[1];

            // location_logs DB 저장 (기존 DeliveryService 활용)
            deliveryService.saveLocation(
                    deliveryId,
                    BigDecimal.valueOf(lat),
                    BigDecimal.valueOf(lng)
            );

            log.info("🚚 [{}/{}] 위치 이동: lat={}, lng={}", i + 1, waypoints.size(), lat, lng);

            try {
                Thread.sleep(3000); // 3초 간격 이동
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("🎉 [{}] 배달 도착! 자동 배송 완료 처리를 진행합니다.", deliveryId);

        // 🚀 이동 완주 후 자동으로 배송 완료(POD) 처리 및 카프카/슬랙 이벤트 발행
        try {
            String dummyPodUrl = "https://scm-delivery-pod-bucket.s3.ap-northeast-2.amazonaws.com/pod/sample-proof.jpg";
            deliveryService.completeDeliveryWithPod(deliveryId, dummyPodUrl);
            log.info("✅ [{}] 자동 배송 완료 처리 및 카프카/슬랙 알림 전송 성공!", deliveryId);
        } catch (Exception e) {
            log.error("❌ [{}] 자동 배송 완료 처리 중 오류 발생: {}", deliveryId, e.getMessage(), e);
        }
    }

    // 주소 변환 메서드 (OpenStreetMap 검색 성공률 향상 처리)
    private double[] getCoordsFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return new double[]{37.5283, 126.9681}; // 기본값: 용산역 좌표
        }

        // 지번 상세 번호(220-5 등)를 빼고 "서울 용산구 서계동" 정제하여 검색 성공률 향상
        String cleanedAddress = address.replaceAll("-\\d+", "").trim();

        try {
            String url = "https://nominatim.openstreetmap.org/search?q=" + cleanedAddress + "&format=json";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SCM-Delivery-App");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, List.class);

            List<Map<String, Object>> body = response.getBody();
            if (body != null && !body.isEmpty()) {
                double lat = Double.parseDouble(body.get(0).get("lat").toString());
                double lon = Double.parseDouble(body.get(0).get("lon").toString());
                log.info("📍 주소 변환 성공: {} -> ({}, {})", cleanedAddress, lat, lon);
                return new double[]{lat, lon};
            }
        } catch (Exception e) {
            log.warn("주소 1차 변환 실패, 2차 기본 구 단위 변환 시도: {}", e.getMessage());
        }

        // 1차 실패 시 구/동 이름만 잘라서 2차 시도 (예: "서울 용산구")
        try {
            String[] parts = cleanedAddress.split(" ");
            if (parts.length >= 2) {
                String shortAddress = parts[0] + " " + parts[1];
                String url = "https://nominatim.openstreetmap.org/search?q=" + shortAddress + "&format=json";

                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "SCM-Delivery-App");
                HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

                ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, List.class);
                List<Map<String, Object>> body = response.getBody();
                if (body != null && !body.isEmpty()) {
                    double lat = Double.parseDouble(body.get(0).get("lat").toString());
                    double lon = Double.parseDouble(body.get(0).get("lon").toString());
                    log.info("📍 구 단위 주소 변환 성공: {} -> ({}, {})", shortAddress, lat, lon);
                    return new double[]{lat, lon};
                }
            }
        } catch (Exception e) {
            log.warn("2차 변환도 실패: {}", e.getMessage());
        }

        log.warn("⚠️ 최종 주소 변환 실패로 용산역 좌표(37.5283, 126.9681)를 목적지로 사용합니다.");
        return new double[]{37.5283, 126.9681}; // 실패 시 서울 용산역 좌표 반환
    }

    // 도로 경로 받아오는 메서드 (OSRM)
    private List<double[]> getRouteWaypoints(double startLat, double startLng, double endLat, double endLng) {
        List<double[]> waypoints = new ArrayList<>();
        try {
            String url = String.format("http://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                    startLng, startLat, endLng, endLat);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SCM-Delivery-App");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                List<Map<String, Object>> routes = (List<Map<String, Object>>) responseBody.get("routes");
                if (routes != null && !routes.isEmpty()) {
                    Map<String, Object> geometry = (Map<String, Object>) routes.get(0).get("geometry");
                    List<List<Double>> coordinates = (List<List<Double>>) geometry.get("coordinates");

                    for (List<Double> coord : coordinates) {
                        waypoints.add(new double[]{coord.get(1), coord.get(0)});
                    }
                    return waypoints;
                }
            }
        } catch (Exception e) {
            log.warn("경로 생성 실패, 직선 보간 좌표 생성: {}", e.getMessage());
        }

        for (int i = 0; i <= 20; i++) {
            double lat = startLat + (endLat - startLat) * (i / 20.0);
            double lng = startLng + (endLng - startLng) * (i / 20.0);
            waypoints.add(new double[]{lat, lng});
        }
        return waypoints;
    }
}