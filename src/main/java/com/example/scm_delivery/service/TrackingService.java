package com.example.scm_delivery.service;

import com.example.scm_delivery.dto.LocationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final double AVERAGE_SPEED_KMH = 40.0;

    // 위치 저장 (Redis Key: "location:{deliveryId}")
    public void saveLocation(LocationDto locationDto) {
        if (locationDto == null || locationDto.getDeliveryId() == null) {
            return;
        }

        // 좌표 유효성 검사 및 거리/시간 계산
        if (isValidCoordinate(locationDto)) {
            double distance = calculateDistanceKm(
                    locationDto.getLatitude(), locationDto.getLongitude(),
                    locationDto.getDestinationLat(), locationDto.getDestinationLng()
            );

            int estimatedMinutes = (int) Math.round((distance / AVERAGE_SPEED_KMH) * 60);

            locationDto.setDistanceKm(Math.round(distance * 10.0) / 10.0);
            locationDto.setEstimatedMinutes(estimatedMinutes);
        }

        // Redis 저장 (10분 만료)
        String key = "location:" + locationDto.getDeliveryId();
        redisTemplate.opsForValue().set(key, locationDto, Duration.ofMinutes(10));
    }

    // 위치 조회
    public LocationDto getLocation(String deliveryId) {
        String key = "location:" + deliveryId;
        return (LocationDto) redisTemplate.opsForValue().get(key);
    }

    // 좌표 Null 및 유효성 체크
    private boolean isValidCoordinate(LocationDto dto) {
        return dto.getLatitude() != null && dto.getLongitude() != null
                && dto.getDestinationLat() != null && dto.getDestinationLng() != null;
    }

    /**
     * 하버사인 공식(Haversine Formula)을 이용한 두 위경도 간의 직선 거리(km) 계산
     */
    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구 반지름 (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}