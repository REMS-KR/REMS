package com.example.REMS.Service;

import com.example.REMS.Entity.CustomerEntity;
import com.example.REMS.Repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 고객 일정 기반 푸시 알림.
 *  · 미팅/본계약: 시작 1시간 전
 *  · 잔금/입주  : 당일 오전 9시
 * 중복 발송은 각 notified* 플래그로 방지.
 *
 * ※ 스케줄이 동작하려면 메인 애플리케이션 클래스에 @EnableScheduling 이 있어야 함.
 */
@Component
@RequiredArgsConstructor
public class CustomerNotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CustomerNotificationScheduler.class);

    private final CustomerRepository customerRepository;
    private final PushService pushService;

    // 서버(JVM) 타임존이 UTC 여도 항상 한국 시간 기준으로 계산한다.
    private static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");

    private static LocalDateTime nowKst() {
        return LocalDateTime.now(KST);
    }

    private static String nameOf(CustomerEntity c) {
        return (c.getName() != null && !c.getName().trim().isEmpty()) ? c.getName().trim() : "고객";
    }

    private static String ownerUid(CustomerEntity c) {
        return (c.getOwner() != null) ? c.getOwner().getUid() : null;
    }

    // 매 분 실행 — 미팅/본계약 1시간 전
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @Transactional
    public void notifyOneHourBefore() {
        LocalDateTime now = nowKst().truncatedTo(ChronoUnit.MINUTES);

        // "1시간 뒤" 지점을 노리되, 서버 재시작·지연으로 그 1분을 놓쳐도 받도록 창을 넓힌다.
        //  - 이미 지나간 일정(now 이전)은 제외
        //  - now ~ now+60분 사이에 시작하는 미발송 건이면 발송 (늦더라도 알림은 간다)
        LocalDateTime start = now;
        LocalDateTime end = now.plusMinutes(60);

        List<CustomerEntity> meetings = customerRepository
                .findByMeetingAtBetweenAndNotifiedMeetingFalse(start, end);
        for (CustomerEntity c : meetings) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "1시간 뒤 " + nameOf(c) + "님과 미팅이 있습니다", "meeting");
            c.setNotifiedMeeting(true);
        }

        List<CustomerEntity> contracts = customerRepository
                .findByContractAtBetweenAndNotifiedContractFalse(start, end);
        for (CustomerEntity c : contracts) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "1시간 뒤 " + nameOf(c) + "님 본계약이 있습니다", "contract");
            c.setNotifiedContract(true);
        }

        if (!meetings.isEmpty() || !contracts.isEmpty()) {
            logger.info("1시간 전 알림 발송 - 미팅 {}건, 본계약 {}건 (기준시각 KST {})",
                    meetings.size(), contracts.size(), now);
        } else {
            logger.debug("1시간 전 알림 대상 없음 - 검사구간 KST {} ~ {}", start, end);
        }
    }

    // 스케줄러가 실제로 등록/기동됐는지 확인용 (없으면 @EnableScheduling 누락)
    @jakarta.annotation.PostConstruct
    public void onStart() {
        logger.info("알림 스케줄러 로드됨 - 서버 기본 타임존={}, 현재 KST={}",
                java.time.ZoneId.systemDefault(), nowKst());
    }

    // 매일 오전 11시 — 잔금/입주 당일 알림
    @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Seoul")
    @Transactional
    public void notifyToday() {
        LocalDate today = nowKst().toLocalDate();

        List<CustomerEntity> balances = customerRepository
                .findByBalanceOnAndNotifiedBalanceFalse(today);
        for (CustomerEntity c : balances) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "오늘은 " + nameOf(c) + "님 잔금이 있습니다", "balance");
            c.setNotifiedBalance(true);
        }

        List<CustomerEntity> moveIns = customerRepository
                .findByMoveInOnAndNotifiedMoveInFalse(today);
        for (CustomerEntity c : moveIns) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "오늘은 " + nameOf(c) + "님 입주가 있습니다", "movein");
            c.setNotifiedMoveIn(true);
        }

        logger.info("당일 알림 발송 - 잔금 {}건, 입주 {}건", balances.size(), moveIns.size());
    }
}
