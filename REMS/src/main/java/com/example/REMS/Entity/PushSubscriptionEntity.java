package com.example.REMS.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Web Push(VAPID) 구독 정보 — 사용자(uid)별 브라우저 구독 endpoint + 암호화 키.
 * 한 사용자가 여러 기기/브라우저를 구독할 수 있어 uid:구독 = 1:N.
 */
@Entity(name = "push_subscriptions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class PushSubscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;                     // 구독한 사용자 uid

    @Column(length = 700, unique = true)
    private String endpoint;                // 푸시 서비스 endpoint (브라우저별 고유)

    @Column(length = 255)
    private String p256dh;                   // 구독 공개키(base64url)

    @Column(length = 255)
    private String auth;                     // 인증 시크릿(base64url)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
