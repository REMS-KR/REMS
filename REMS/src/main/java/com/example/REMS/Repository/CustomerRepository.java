package com.example.REMS.Repository;

import com.example.REMS.Entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
    // 작성자 본인의 고객 목록 (최근 등록 순)
    List<CustomerEntity> findByOwner_UidOrderByIdDesc(String uid);

    // ===== 푸시 알림 스케줄러용 =====
    // 알림이 남아있을 수 있는 일정 보유 고객만 추린다.
    // (시점 계산은 사용자별 설정에 따라 달라지므로 스케줄러에서 판단)
    @Query("SELECT c FROM customers c WHERE " +
           "(c.meetingAt  IS NOT NULL AND (c.notifiedMeeting  = false OR c.notifiedMeeting2  = false)) OR " +
           "(c.contractAt IS NOT NULL AND (c.notifiedContract = false OR c.notifiedContract2 = false)) OR " +
           "(c.balanceOn  IS NOT NULL AND (c.notifiedBalance  = false OR c.notifiedBalance2  = false)) OR " +
           "(c.moveInOn   IS NOT NULL AND (c.notifiedMoveIn   = false OR c.notifiedMoveIn2   = false))")
    List<CustomerEntity> findPendingNotifications();
}
