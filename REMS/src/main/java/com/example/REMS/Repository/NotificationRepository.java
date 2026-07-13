package com.example.REMS.Repository;

import com.example.REMS.Entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    // 최근 알림 목록 (최신순)
    List<NotificationEntity> findTop50ByUidOrderByIdDesc(String uid);

    // 안 읽은 개수 (배지용)
    long countByUidAndReadFlagFalse(String uid);

    List<NotificationEntity> findByUidAndReadFlagFalse(String uid);
}
