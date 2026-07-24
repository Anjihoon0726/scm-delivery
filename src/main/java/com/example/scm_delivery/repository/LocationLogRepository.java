package com.example.scm_delivery.repository;

import com.example.scm_delivery.domain.LocationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationLogRepository extends JpaRepository<LocationLog, Long> {
    // 특정 배송건의 위치 기록들을 조회하기 위한 기능
    List<LocationLog> findByDeliveryIdOrderByLoggedAtDesc(String deliveryId);
}
