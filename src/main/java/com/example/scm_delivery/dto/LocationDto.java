package com.example.scm_delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {
    private String deliveryId;        // 배송 ID
    private Double latitude;          // 현재 위도
    private Double longitude;         // 현재 경도

    // 💡 서버에서 계산 후 프론트/관제 대시보드로 전달할 때 채워지는 필드
    private Double destinationLat;    // 배송지 위도
    private Double destinationLng;    // 배송지 경도
    private Double distanceKm;        // 잔여 거리 (km)
    private Integer estimatedMinutes; // 예상 소요 시간 (분)

    // 위치 갱신용 생성자
    public LocationDto(String deliveryId, Double latitude, Double longitude) {
        this.deliveryId = deliveryId;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}