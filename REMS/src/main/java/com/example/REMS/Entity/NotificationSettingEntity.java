package com.example.REMS.Entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자별 알림 시점 설정.
 *  · 4개 이벤트(미팅/본계약/잔금/입주) 각각 1차(필수) + 2차(선택) 알림 시점.
 *  · 값은 "이벤트 시작 몇 분 전"(minutes). 잔금/입주는 날짜만 있으므로
 *    "당일 몇 시"(hour, 0~23) 또는 "N일 전 몇 시" 형태로 저장한다.
 *
 *  meeting/contract : lead1Min, lead2Min  (분 단위, 예: 60 = 1시간 전)
 *  balance/moveIn   : day1Before + hour1 / day2Before + hour2
 *                     (예: dayBefore=0, hour=11 → 당일 오전 11시)
 *  2차 알림은 null 이면 사용 안 함.
 */
@Entity(name = "notification_settings")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class NotificationSettingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String uid;

    // ===== 미팅 (시각이 있는 일정) — 시작 N분 전 =====
    @Builder.Default private Integer meetingLead1 = 60;    // 필수(기본 1시간 전)
    private Integer meetingLead2;                          // 선택(null = 미사용)

    // ===== 본계약 =====
    @Builder.Default private Integer contractLead1 = 60;
    private Integer contractLead2;

    // ===== 잔금 (날짜만 있는 일정) — N일 전 H시 =====
    @Builder.Default private Integer balanceDay1 = 0;      // 0 = 당일
    @Builder.Default private Integer balanceHour1 = 11;    // 오전 11시
    private Integer balanceDay2;                           // 선택
    private Integer balanceHour2;

    // ===== 입주 =====
    @Builder.Default private Integer moveInDay1 = 0;
    @Builder.Default private Integer moveInHour1 = 11;
    private Integer moveInDay2;
    private Integer moveInHour2;
}
