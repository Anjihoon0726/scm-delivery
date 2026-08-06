package com.example.scm_delivery.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SimulationRequestDto {
    private String deliveryId;
    private String destinationAddress;
}