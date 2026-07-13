package com.example.REMS.Repository;

import com.example.REMS.Entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
    // 작성자 본인의 고객 목록 (최근 등록 순)
    List<CustomerEntity> findByOwner_UidOrderByIdDesc(String uid);

    // ===== 푸시 알림 스케줄러용 =====
    // 미팅/본계약: 특정 구간(1시간 뒤 그 1분)에 해당하고 아직 미발송
    List<CustomerEntity> findByMeetingAtBetweenAndNotifiedMeetingFalse(LocalDateTime start, LocalDateTime end);
    List<CustomerEntity> findByContractAtBetweenAndNotifiedContractFalse(LocalDateTime start, LocalDateTime end);

    // 잔금/입주: 오늘 날짜이고 아직 미발송
    List<CustomerEntity> findByBalanceOnAndNotifiedBalanceFalse(LocalDate date);
    List<CustomerEntity> findByMoveInOnAndNotifiedMoveInFalse(LocalDate date);
}
