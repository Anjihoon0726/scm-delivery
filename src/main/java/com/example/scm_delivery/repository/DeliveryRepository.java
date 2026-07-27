package com.example.scm_delivery.repository;

import com.example.scm_delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, String> {

    Optional<Delivery> findByTrackingNumber(String trackingNumber);
}