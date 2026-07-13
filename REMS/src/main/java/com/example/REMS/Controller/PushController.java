package com.example.REMS.Controller;

import com.example.REMS.Entity.PushSubscriptionEntity;
import com.example.REMS.Repository.PushSubscriptionRepository;
import com.example.REMS.Service.PushService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Web Push 구독 관리.
 *  GET  /push/public-key           → VAPID 공개키 (프론트 구독 시 applicationServerKey)
 *  POST /push/subscribe/{uid}      → 구독 저장 (body: {endpoint, keys:{p256dh, auth}})
 *  POST /push/unsubscribe/{uid}    → 구독 해제 (body: {endpoint})
 *  POST /push/test/{uid}           → 테스트 발송
 */
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;
    private final PushSubscriptionRepository subscriptionRepository;
    private final com.example.REMS.Repository.NotificationRepository notificationRepository;

    // 공개키는 인증 없이 접근 가능해야 함 (SecurityConfig 에서 permitAll 필요)
    @Operation(summary = "VAPID 공개키 조회")
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> publicKey() {
        return ResponseEntity.ok(Map.of("publicKey", pushService.getPublicKey()));
    }

    @SuppressWarnings("unchecked")
    @Operation(summary = "푸시 구독 저장")
    @PostMapping("/subscribe/{uid}")
    @Transactional
    public ResponseEntity<Map<String, Object>> subscribe(@PathVariable("uid") String uid,
                                                         @RequestBody Map<String, Object> body,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        String endpoint = (String) body.get("endpoint");
        Map<String, Object> keys = (Map<String, Object>) body.get("keys");
        if (endpoint == null || keys == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 구독 정보입니다"));
        }
        String p256dh = (String) keys.get("p256dh");
        String auth = (String) keys.get("auth");

        PushSubscriptionEntity sub = subscriptionRepository.findByEndpoint(endpoint)
                .orElseGet(PushSubscriptionEntity::new);
        sub.setUid(uid);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        subscriptionRepository.save(sub);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "푸시 구독 해제")
    @PostMapping("/unsubscribe/{uid}")
    @Transactional
    public ResponseEntity<Map<String, Object>> unsubscribe(@PathVariable("uid") String uid,
                                                           @RequestBody Map<String, Object> body,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        String endpoint = (String) body.get("endpoint");
        if (endpoint != null) subscriptionRepository.deleteByEndpoint(endpoint);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "테스트 푸시 발송")
    @PostMapping("/test/{uid}")
    public ResponseEntity<Map<String, Object>> test(@PathVariable("uid") String uid,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        pushService.sendToUser(uid, "핵방노트", "푸시 알림이 정상적으로 설정되었습니다 🎉", "test");
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ===== 알림 목록 =====
    @Operation(summary = "알림 목록 (최근 50건)")
    @GetMapping("/notifications/{uid}")
    public ResponseEntity<java.util.List<Map<String, Object>>> list(@PathVariable("uid") String uid,
                                                                    @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        var list = notificationRepository.findTop50ByUidOrderByIdDesc(uid).stream().map(n -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("body", n.getBody());
            m.put("type", n.getType());
            m.put("read", Boolean.TRUE.equals(n.getReadFlag()));
            m.put("createdAt", n.getCreatedAt() != null
                    ? n.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "안 읽은 알림 개수")
    @GetMapping("/notifications/{uid}/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(@PathVariable("uid") String uid,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        return ResponseEntity.ok(Map.of("count", notificationRepository.countByUidAndReadFlagFalse(uid)));
    }

    @Operation(summary = "알림 전체 읽음 처리")
    @PostMapping("/notifications/{uid}/read-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> readAll(@PathVariable("uid") String uid,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        var list = notificationRepository.findByUidAndReadFlagFalse(uid);
        list.forEach(n -> n.setReadFlag(true));
        notificationRepository.saveAll(list);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "알림 전체 삭제")
    @DeleteMapping("/notifications/{uid}")
    @Transactional
    public ResponseEntity<Map<String, Object>> clear(@PathVariable("uid") String uid,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        notificationRepository.deleteAll(notificationRepository.findTop50ByUidOrderByIdDesc(uid));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private void checkAuth(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
    }
}
