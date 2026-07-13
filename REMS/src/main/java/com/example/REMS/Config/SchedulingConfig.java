package com.example.REMS.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화.
 *  · 메인 애플리케이션 클래스에 @EnableScheduling 을 붙이지 않아도
 *    이 설정 클래스만 있으면 @Scheduled 가 동작한다.
 *  · 알림 스케줄러(CustomerNotificationScheduler)가 이 설정에 의존한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
