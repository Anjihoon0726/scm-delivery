package com.example.scm_delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pods")
@Getter
@NoArgsConstructor
public class Pod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pod_id")
    private Long podId;

    @Column(name = "delivery_id", nullable = false)
    private String deliveryId;

    @Column(name = "s3_image_url", nullable = false, length = 500)
    private String s3ImageUrl;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    public Pod(String deliveryId, String s3ImageUrl) {
        this.deliveryId = deliveryId;
        this.s3ImageUrl = s3ImageUrl;
        this.signedAt = LocalDateTime.now();
    }
}