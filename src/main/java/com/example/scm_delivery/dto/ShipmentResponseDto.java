package com.example.scm_delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentResponseDto {
    private String status;
    private String courierCode;
    private String trackingNumber;
    private String createdAt;
}