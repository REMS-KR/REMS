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

    @Value("${push.vapid.public-key}")
    private String publicKey;

    @Value("${push.vapid.private-key}")
    private String privateKey;

    @Value("${push.vapid.subject:mailto:admin@haekbangnote.app}")
    private String subject;

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper om = new ObjectMapper();

    private nl.martijndwars.webpush.PushService pushService;

    @PostConstruct
    public void init() {
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
        if (uid == null || pushService == null) return;
        var subs = subscriptionRepository.findByUid(uid);
        if (subs.isEmpty()) return;

        String payload;
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", title);
            m.put("body", body);
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
