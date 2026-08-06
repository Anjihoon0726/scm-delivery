package com.example.scm_delivery.repository;

import com.example.scm_delivery.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    // processAt이 현재 시간보다 작거나 같고, status가 INIT인 이벤트 조회
    List<OutboxEvent> findByStatusAndProcessAtLessThanEqual(String status, LocalDateTime now);
}