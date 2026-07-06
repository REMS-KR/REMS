package com.example.REMS.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 고객(리드) 관리 (중개사 전용).
 *  · 계약자(TenantEntity)와 독립된 잠재고객 상담 리스트.
 *  · 통화 녹음 → AI 요약으로 자동 채움 가능. 감도(sensitivity)는 수기 체크.
 *  · 작성자(owner)만 조회/수정/삭제.
 */
@Entity(name = "customers")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class CustomerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String summary;         // 요약 (통화/상담 내용 요약)
    private String phone;           // 전화번호
    private String sensitivity;     // 감도 (high/mid/low) — 수기 체크
    private String amount;          // 금액 (예: "매매 3억", "보 5000/월 50")
    private String moveInDate;      // 입주 희망일 (yyyy-MM-dd 또는 자유 서술)
    private String location;        // 희망 위치
    private String loan;            // 대출 (예: "버팀목 희망", "가능")
    private String meetingDate;     // 미팅 날짜 (yyyy-MM-dd 또는 자유 서술)

    @Column(length = 1000)
    private String memo;            // 메모

    // 작성자(소유자) — users 테이블과 N:1 외래키 (owner_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonIgnore
    private UserEntity owner;

    // 등록 일시 — 최초 저장 시 자동 기록
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
