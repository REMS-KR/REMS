package com.example.REMS.Service;

import com.example.REMS.Entity.PushSubscriptionEntity;
import com.example.REMS.Repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web Push(VAPID) 발송. nl.martijndwars:web-push 사용.
 * build.gradle 에 의존성 추가 필요:
 *   implementation 'nl.martijndwars:web-push:5.1.1'
 *   implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
 */
@Service
@RequiredArgsConstructor
public class PushService {

    private static final Logger logger = LoggerFactory.getLogger(PushService.class);

    @Value("${push.vapid.public-key:}")
    private String publicKey;

    @Value("${push.vapid.private-key:}")
    private String privateKey;

    @Value("${push.vapid.subject:mailto:admin@haekbangnote.app}")
    private String subject;

    private final PushSubscriptionRepository subscriptionRepository;
    private final com.example.REMS.Repository.NotificationRepository notificationRepository;
    private final ObjectMapper om = new ObjectMapper();

    private nl.martijndwars.webpush.PushService pushService;

    @PostConstruct
    public void init() {
        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) {
            logger.warn("VAPID 키가 설정되지 않아 푸시 알림이 비활성화됩니다. " +
                    "application.yml 의 push.vapid.public-key / private-key 를 확인하세요.");
            return;   // 앱은 정상 기동 (푸시만 비활성)
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            pushService = new nl.martijndwars.webpush.PushService()
                    .setPublicKey(publicKey)
                    .setPrivateKey(privateKey)
                    .setSubject(subject);
            logger.info("PushService 초기화 완료 (VAPID)");
        } catch (Exception e) {
            logger.error("PushService 초기화 실패 - VAPID 키 확인 필요", e);
        }
    }

    public String getPublicKey() {
        return publicKey;
    }

    /** 특정 사용자의 모든 구독 기기에 알림 발송. title/body 는 SW 가 그대로 표시. */
    @Transactional
    public void sendToUser(String uid, String title, String body) {
        sendToUser(uid, title, body, null);
    }

    /** type: meeting/contract/balance/movein/test — 알림 목록 분류용 */
    @Transactional
    public void sendToUser(String uid, String title, String body, String type) {
        sendToUser(uid, title, body, type, null);
    }

    /** customerId: 알림 클릭 시 이동할 고객 id (없으면 null) */
    @Transactional
    public void sendToUser(String uid, String title, String body, String type, Long customerId) {
        if (uid == null) return;

        // 1) 알림 이력 저장 (푸시 미설정/미구독이어도 앱 '알림' 목록에는 남는다)
        try {
            notificationRepository.save(com.example.REMS.Entity.NotificationEntity.builder()
                    .uid(uid).title(title).body(body).type(type)
                    .customerId(customerId).readFlag(false).build());
        } catch (Exception e) {
            logger.warn("알림 이력 저장 실패 - uid={}", uid, e);
        }

        // 2) Web Push 발송
        if (pushService == null) return;   // VAPID 미설정 → 푸시 스킵
        var subs = subscriptionRepository.findByUid(uid);
        if (subs.isEmpty()) return;

        String payload;
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", title);
            m.put("body", body);
            m.put("type", type);
            if (customerId != null) m.put("customerId", customerId);   // SW 가 클릭 시 사용
            payload = om.writeValueAsString(m);
        } catch (Exception e) {
            payload = "{\"title\":\"핵방노트\",\"body\":\"" + (body == null ? "" : body) + "\"}";
        }

        for (PushSubscriptionEntity s : subs) {
            try {
                Subscription subscription = new Subscription(
                        s.getEndpoint(), new Subscription.Keys(s.getP256dh(), s.getAuth()));
                var resp = pushService.send(new Notification(subscription, payload));
                int code = resp.getStatusLine().getStatusCode();
                if (code == 404 || code == 410) {   // 만료/폐기된 구독 → 정리
                    subscriptionRepository.deleteByEndpoint(s.getEndpoint());
                    logger.info("만료된 푸시 구독 삭제 - uid={}, code={}", uid, code);
                } else if (code >= 400) {
                    logger.warn("푸시 발송 실패 - uid={}, code={}", uid, code);
                }
            } catch (Exception e) {
                logger.warn("푸시 발송 예외 - uid={}, endpoint={}", uid, s.getEndpoint(), e);
            }
        }
    }
}
