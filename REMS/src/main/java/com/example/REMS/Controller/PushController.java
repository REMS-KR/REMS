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
    private final com.example.REMS.Service.CustomerNotificationScheduler scheduler;
    private final com.example.REMS.Repository.NotificationSettingRepository settingRepository;

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
        pushService.sendToUser(uid, "핵방노트", "푸시 알림이 정상적으로 설정되었습니다", "test");
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
            m.put("customerId", n.getCustomerId());
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

    // ===== 진단: 스케줄 상태 확인 & 수동 실행 =====
    @Operation(summary = "알림 스케줄 진단 (서버시각/예정건 확인)")
    @GetMapping("/debug/{uid}")
    public ResponseEntity<Map<String, Object>> debug(@PathVariable("uid") String uid,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        var kst = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("serverZone", java.time.ZoneId.systemDefault().toString());
        m.put("serverNow", java.time.LocalDateTime.now().toString());
        m.put("kstNow", kst.toString());
        m.put("pushConfigured", pushService.getPublicKey() != null && !pushService.getPublicKey().isBlank());
        m.put("mySubscriptions", subscriptionRepository.findByUid(uid).size());
        return ResponseEntity.ok(m);
    }

    @Operation(summary = "알림 스케줄러 수동 실행 (테스트)")
    @PostMapping("/debug/{uid}/run")
    public ResponseEntity<Map<String, Object>> runScheduler(@PathVariable("uid") String uid,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        scheduler.notifyOneHourBefore();
        scheduler.notifyToday();
        return ResponseEntity.ok(Map.of("ok", true, "message", "스케줄러를 수동 실행했습니다. 알림 목록을 확인하세요."));
    }

    // ===== 알림 시점 설정 =====
    @Operation(summary = "알림 시점 설정 조회")
    @GetMapping("/settings/{uid}")
    @Transactional
    public ResponseEntity<com.example.REMS.Entity.NotificationSettingEntity> getSettings(
            @PathVariable("uid") String uid,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        var s = settingRepository.findByUid(uid).orElseGet(() ->
                settingRepository.save(com.example.REMS.Entity.NotificationSettingEntity.builder().uid(uid).build()));
        return ResponseEntity.ok(s);
    }

    @Operation(summary = "알림 시점 설정 저장")
    @PutMapping("/settings/{uid}")
    @Transactional
    public ResponseEntity<com.example.REMS.Entity.NotificationSettingEntity> saveSettings(
            @PathVariable("uid") String uid,
            @RequestBody com.example.REMS.Entity.NotificationSettingEntity body,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkAuth(uid, userDetails);
        var s = settingRepository.findByUid(uid).orElseGet(() ->
                com.example.REMS.Entity.NotificationSettingEntity.builder().uid(uid).build());

        // 1차(필수): 값이 없으면 기존값 유지, 그래도 없으면 기본값
        s.setMeetingLead1(req(body.getMeetingLead1(), s.getMeetingLead1(), 60));
        s.setContractLead1(req(body.getContractLead1(), s.getContractLead1(), 60));
        s.setBalanceDay1(req(body.getBalanceDay1(), s.getBalanceDay1(), 0));
        s.setBalanceHour1(req(body.getBalanceHour1(), s.getBalanceHour1(), 11));
        s.setMoveInDay1(req(body.getMoveInDay1(), s.getMoveInDay1(), 0));
        s.setMoveInHour1(req(body.getMoveInHour1(), s.getMoveInHour1(), 11));

        // 2차(선택): null 이면 '사용 안 함'으로 그대로 저장
        s.setMeetingLead2(body.getMeetingLead2());
        s.setContractLead2(body.getContractLead2());
        s.setBalanceDay2(body.getBalanceDay2());
        s.setBalanceHour2(body.getBalanceDay2() == null ? null
                : (body.getBalanceHour2() == null ? 11 : body.getBalanceHour2()));
        s.setMoveInDay2(body.getMoveInDay2());
        s.setMoveInHour2(body.getMoveInDay2() == null ? null
                : (body.getMoveInHour2() == null ? 11 : body.getMoveInHour2()));

        s.setUid(uid);
        return ResponseEntity.ok(settingRepository.save(s));
    }

    private Integer req(Integer incoming, Integer current, Integer fallback) {
        if (incoming != null) return incoming;
        return current != null ? current : fallback;
    }

    private void checkAuth(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
    }
}
