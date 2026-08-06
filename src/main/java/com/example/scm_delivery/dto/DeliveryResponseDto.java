package com.example.scm_delivery.dto;

import com.example.scm_delivery.domain.Delivery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryResponseDto {
    private String orderId;
    private String trackingNumber;
    private String status;
    private String deliveredAt;
    private String proofImageUrl;

    // 📦 [신규 추가] 보내는 사람(출발지) 정보
    private String senderName;
    private String senderPhone;
    private String senderAddress;

    // 🛠️ Delivery 엔티티를 DTO로 편리하게 변환하는 정적 팩토리 메서드
    public static DeliveryResponseDto from(Delivery delivery, String proofImageUrl) {
        return DeliveryResponseDto.builder()
                .orderId(delivery.getOrderId())
                .trackingNumber(delivery.getTrackingNumber())
                .status(delivery.getStatus())
                .deliveredAt(delivery.getDeliveredAt() != null ? delivery.getDeliveredAt().toString() : null)
                .proofImageUrl(proofImageUrl)
                // 보내는 사람 정보 매핑
                .senderName(delivery.getSenderName())
                .senderPhone(delivery.getSenderPhone())
                .senderAddress(delivery.getSenderAddress())
                .build();
    }
}