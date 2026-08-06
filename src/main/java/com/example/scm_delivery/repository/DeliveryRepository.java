package com.example.scm_delivery.repository;

import com.example.scm_delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, String> {

    // PK가 trackingNumber이므로 findById(trackingNumber)를 기본 제공함

    // 만약 orderId나 deliveryId로 조회할 일이 있다면 아래 메서드들 활용
    Optional<Delivery> findByOrderId(String orderId);
    Optional<Delivery> findByDeliveryId(String deliveryId);
}