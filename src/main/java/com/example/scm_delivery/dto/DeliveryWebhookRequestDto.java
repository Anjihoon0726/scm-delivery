package com.example.scm_delivery.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryWebhookRequestDto {
    private String orderId;
    private String trackingNumber;
    private String deliveredAt;
    private String proofImageUrl;
}