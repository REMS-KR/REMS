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

    private static String nameOf(CustomerEntity c) {
        return (c.getName() != null && !c.getName().trim().isEmpty()) ? c.getName().trim() : "고객";
    }

    private static String ownerUid(CustomerEntity c) {
        return (c.getOwner() != null) ? c.getOwner().getUid() : null;
    }

    // 매 분 실행 — 미팅/본계약 1시간 전
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void notifyOneHourBefore() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = now.plusMinutes(59);   // 1시간 전 ±1분 창(플래그로 중복 방지)
        LocalDateTime end = now.plusMinutes(61);

        List<CustomerEntity> meetings = customerRepository
                .findByMeetingAtBetweenAndNotifiedMeetingFalse(start, end);
        for (CustomerEntity c : meetings) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "1시간 뒤 " + nameOf(c) + "님과 미팅이 있습니다");
            c.setNotifiedMeeting(true);
        }

        List<CustomerEntity> contracts = customerRepository
                .findByContractAtBetweenAndNotifiedContractFalse(start, end);
        for (CustomerEntity c : contracts) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "1시간 뒤 " + nameOf(c) + "님 본계약이 있습니다");
            c.setNotifiedContract(true);
        }

        if (!meetings.isEmpty() || !contracts.isEmpty()) {
            logger.info("1시간 전 알림 발송 - 미팅 {}건, 본계약 {}건", meetings.size(), contracts.size());
        }
    }

    // 매일 오전 9시 — 잔금/입주 당일 알림
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void notifyToday() {
        LocalDate today = LocalDate.now();

        List<CustomerEntity> balances = customerRepository
                .findByBalanceOnAndNotifiedBalanceFalse(today);
        for (CustomerEntity c : balances) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "오늘은 " + nameOf(c) + "님 잔금이 있습니다");
            c.setNotifiedBalance(true);
        }

        List<CustomerEntity> moveIns = customerRepository
                .findByMoveInOnAndNotifiedMoveInFalse(today);
        for (CustomerEntity c : moveIns) {
            String uid = ownerUid(c);
            if (uid != null) pushService.sendToUser(uid, "핵방노트", "오늘은 " + nameOf(c) + "님 입주가 있습니다");
            c.setNotifiedMoveIn(true);
        }

        logger.info("당일 알림 발송 - 잔금 {}건, 입주 {}건", balances.size(), moveIns.size());
    }
}
