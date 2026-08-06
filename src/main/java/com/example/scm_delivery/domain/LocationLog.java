package com.example.scm_delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "location_logs")
@Getter
@NoArgsConstructor
public class LocationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "delivery_id", nullable = false)
    private String deliveryId;

    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    public LocationLog(String deliveryId, BigDecimal latitude, BigDecimal longitude) {
        this.deliveryId = deliveryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.loggedAt = LocalDateTime.now();
    }
}