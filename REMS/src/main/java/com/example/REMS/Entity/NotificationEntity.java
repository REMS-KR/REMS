package com.example.REMS.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 발송된 알림 이력 — 앱 상단 '알림' 목록에 표시.
 */
@Entity(name = "notifications")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;             // 수신자 uid

    private String title;           // "핵방노트"

    @Column(length = 500)
    private String body;            // 알림 본문

    private String type;            // meeting / contract / balance / movein / test

    private Long customerId;        // 관련 고객 id (클릭 시 해당 고객 상세로 이동)

    @Builder.Default
    private Boolean readFlag = false;   // 읽음 여부

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
