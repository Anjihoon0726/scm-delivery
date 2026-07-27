package com.example.scm_delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DeliveryResponseDto {
    private String orderId;
    private String trackingNumber;
    private String status;
    private String deliveredAt;
    private String proofImageUrl;
}