package com.example.REMS.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private String name;            // 이름 (고객명)

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

    // ===== 푸시 알림용 일정 (구조화) =====
    private LocalDateTime meetingAt;   // 미팅 일시 (오전/오후 포함) → 1시간 전 알림
    private LocalDateTime contractAt;  // 본계약 일시 → 1시간 전 알림
    private LocalDate balanceOn;       // 잔금일 → 당일 알림
    private LocalDate moveInOn;        // 입주일 → 당일 알림

    // 중복 발송 방지 플래그 (1차)
    @Builder.Default private Boolean notifiedMeeting = false;
    @Builder.Default private Boolean notifiedContract = false;
    @Builder.Default private Boolean notifiedBalance = false;
    @Builder.Default private Boolean notifiedMoveIn = false;

    // 중복 발송 방지 플래그 (2차 — 사용자가 두 번째 알림을 설정한 경우)
    @Builder.Default private Boolean notifiedMeeting2 = false;
    @Builder.Default private Boolean notifiedContract2 = false;
    @Builder.Default private Boolean notifiedBalance2 = false;
    @Builder.Default private Boolean notifiedMoveIn2 = false;

    // 이 고객에게 연결(추천)된 매물 건물 id 목록
    @ElementCollection
    @CollectionTable(name = "customer_buildings", joinColumns = @JoinColumn(name = "customer_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "building_id")
    @Builder.Default
    private List<Long> buildingIds = new ArrayList<>();

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
