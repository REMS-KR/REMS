package com.example.REMS.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 찜(관심 매물) — 사용자(uid) 와 건물(buildingId)의 관심 관계.
 *  · UserEntity 와 직접 연관 없이 uid 문자열로만 저장(결합도↓, 기존 코드 영향 없음).
 *  · (uid, buildingId) 조합은 유일 — 같은 건물 중복 찜 방지.
 */
@Entity(name = "favorites")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"uid", "buildingId"}))
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class FavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String uid;          // 찜한 사용자

    @Column(nullable = false)
    private Long buildingId;     // 찜한 건물 id

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
