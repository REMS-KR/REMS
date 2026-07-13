package com.example.REMS.Repository;

import com.example.REMS.Entity.NotificationSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSettingEntity, Long> {
    Optional<NotificationSettingEntity> findByUid(String uid);
}
