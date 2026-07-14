package com.example.REMS.Service;

import com.example.REMS.Entity.CustomerEntity;
import com.example.REMS.Entity.NotificationSettingEntity;
import com.example.REMS.Repository.CustomerRepository;
import com.example.REMS.Repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 고객 일정 기반 푸시 알림 — 사용자별 알림 시점 설정(NotificationSettingEntity)을 따른다.
 *  · 미팅/본계약 : 시작 N분 전 (1차 필수 / 2차 선택)
 *  · 잔금/입주   : N일 전 H시  (1차 필수 / 2차 선택)
 *
 * 서버 타임존과 무관하게 항상 한국시간(KST) 기준으로 계산한다.
 */
@Component
@RequiredArgsConstructor
public class CustomerNotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CustomerNotificationScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CustomerRepository customerRepository;
    private final NotificationSettingRepository settingRepository;
    private final PushService pushService;

    private static LocalDateTime nowKst() { return LocalDateTime.now(KST); }

    private static String nameOf(CustomerEntity c) {
        return (c.getName() != null && !c.getName().trim().isEmpty()) ? c.getName().trim() : "고객";
    }
    private static String ownerUid(CustomerEntity c) {
        return (c.getOwner() != null) ? c.getOwner().getUid() : null;
    }

    @jakarta.annotation.PostConstruct
    public void onStart() {
        logger.info("알림 스케줄러 로드됨 - 서버 기본 타임존={}, 현재 KST={}", ZoneId.systemDefault(), nowKst());
    }

    /** 사용자 설정(없으면 기본값) */
    private NotificationSettingEntity settingOf(String uid, Map<String, NotificationSettingEntity> cache) {
        return cache.computeIfAbsent(uid, u ->
                settingRepository.findByUid(u).orElseGet(() ->
                        NotificationSettingEntity.builder().uid(u).build()));   // 기본: 1시간 전 / 당일 11시
    }

    /** "이벤트 시각 - N분" 이 지금(분 단위)과 같거나 이미 지났고, 이벤트가 아직 안 지났으면 발송 대상 */
    private boolean isDue(LocalDateTime eventAt, Integer leadMinutes, LocalDateTime now) {
        if (eventAt == null || leadMinutes == null) return false;
        LocalDateTime fireAt = eventAt.minusMinutes(leadMinutes);
        // 발송 시점이 도래했고(놓친 분도 포함), 이벤트 자체는 아직 미래
        return !now.isBefore(fireAt) && now.isBefore(eventAt);
    }

    /** 날짜형 일정: (날짜 - N일) 의 H시가 도래했고, 이벤트 날짜가 아직 지나지 않았으면 발송 */
    private boolean isDueDate(LocalDate eventOn, Integer daysBefore, Integer hour, LocalDateTime now) {
        if (eventOn == null || daysBefore == null || hour == null) return false;
        LocalDateTime fireAt = eventOn.minusDays(daysBefore).atTime(Math.min(Math.max(hour, 0), 23), 0);
        return !now.isBefore(fireAt) && !now.toLocalDate().isAfter(eventOn);
    }

    /** 남은 시간 표현 (예: "1시간 뒤", "30분 뒤", "내일") */
    private static String leadLabel(int minutes) {
        if (minutes <= 0) return "잠시 뒤";
        if (minutes % 1440 == 0) {
            int d = minutes / 1440;
            return d == 1 ? "내일" : d + "일 뒤";
        }
        if (minutes % 60 == 0) return (minutes / 60) + "시간 뒤";
        return minutes + "분 뒤";
    }

    private static String dayLabel(int daysBefore) {
        if (daysBefore <= 0) return "오늘은";
        if (daysBefore == 1) return "내일은";
        return daysBefore + "일 뒤";
    }

    // 매 분 실행 — 모든 예정 알림을 사용자 설정에 맞춰 검사
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @Transactional
    public void notifyOneHourBefore() {
        LocalDateTime now = nowKst().truncatedTo(ChronoUnit.MINUTES);
        Map<String, NotificationSettingEntity> cache = new HashMap<>();
        int sent = 0;

        for (CustomerEntity c : customerRepository.findPendingNotifications()) {
            String uid = ownerUid(c);
            if (uid == null) continue;
            NotificationSettingEntity s = settingOf(uid, cache);
            String name = nameOf(c);

            // ---- 미팅 ----
            if (!Boolean.TRUE.equals(c.getNotifiedMeeting()) && isDue(c.getMeetingAt(), s.getMeetingLead1(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        leadLabel(s.getMeetingLead1()) + " " + name + "님과 미팅이 있습니다", "meeting", c.getId());
                c.setNotifiedMeeting(true); sent++;
            }
            if (!Boolean.TRUE.equals(c.getNotifiedMeeting2()) && isDue(c.getMeetingAt(), s.getMeetingLead2(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        leadLabel(s.getMeetingLead2()) + " " + name + "님과 미팅이 있습니다", "meeting", c.getId());
                c.setNotifiedMeeting2(true); sent++;
            }

            // ---- 본계약 ----
            if (!Boolean.TRUE.equals(c.getNotifiedContract()) && isDue(c.getContractAt(), s.getContractLead1(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        leadLabel(s.getContractLead1()) + " " + name + "님 본계약이 있습니다", "contract", c.getId());
                c.setNotifiedContract(true); sent++;
            }
            if (!Boolean.TRUE.equals(c.getNotifiedContract2()) && isDue(c.getContractAt(), s.getContractLead2(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        leadLabel(s.getContractLead2()) + " " + name + "님 본계약이 있습니다", "contract", c.getId());
                c.setNotifiedContract2(true); sent++;
            }

            // ---- 잔금 ----
            if (!Boolean.TRUE.equals(c.getNotifiedBalance())
                    && isDueDate(c.getBalanceOn(), s.getBalanceDay1(), s.getBalanceHour1(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        dayLabel(s.getBalanceDay1()) + " " + name + "님 잔금이 있습니다", "balance", c.getId());
                c.setNotifiedBalance(true); sent++;
            }
            if (!Boolean.TRUE.equals(c.getNotifiedBalance2())
                    && isDueDate(c.getBalanceOn(), s.getBalanceDay2(), s.getBalanceHour2(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        dayLabel(s.getBalanceDay2()) + " " + name + "님 잔금이 있습니다", "balance", c.getId());
                c.setNotifiedBalance2(true); sent++;
            }

            // ---- 입주 ----
            if (!Boolean.TRUE.equals(c.getNotifiedMoveIn())
                    && isDueDate(c.getMoveInOn(), s.getMoveInDay1(), s.getMoveInHour1(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        dayLabel(s.getMoveInDay1()) + " " + name + "님 입주가 있습니다", "movein", c.getId());
                c.setNotifiedMoveIn(true); sent++;
            }
            if (!Boolean.TRUE.equals(c.getNotifiedMoveIn2())
                    && isDueDate(c.getMoveInOn(), s.getMoveInDay2(), s.getMoveInHour2(), now)) {
                pushService.sendToUser(uid, "핵방노트",
                        dayLabel(s.getMoveInDay2()) + " " + name + "님 입주가 있습니다", "movein", c.getId());
                c.setNotifiedMoveIn2(true); sent++;
            }
        }

        if (sent > 0) logger.info("알림 발송 {}건 (기준 KST {})", sent, now);
    }

    /** 하위 호환 — 수동 실행용(위 메서드가 잔금/입주까지 모두 처리한다) */
    @Transactional
    public void notifyToday() {
        notifyOneHourBefore();
    }
}
