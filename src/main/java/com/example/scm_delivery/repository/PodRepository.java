package com.example.scm_delivery.repository;

import com.example.scm_delivery.domain.Pod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PodRepository extends JpaRepository<Pod, Long> {
    Optional<Pod> findByDeliveryId(String deliveryId);
}