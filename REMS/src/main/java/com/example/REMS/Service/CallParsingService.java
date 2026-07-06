package com.example.REMS.Service;

import com.example.REMS.DTO.CallDraftDTO;
import com.example.REMS.DTO.CustomerDTO;
import com.example.REMS.Entity.UserPermissionEntity;
import com.example.REMS.Repository.UserPermissionRepository;
import com.example.REMS.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 통화 파싱 오케스트레이션: 인증/권한 -> STT -> LLM 구조화 -> 초안 반환.
 *  - parseCall()         : 계약자(임차인)/건물 초안
 *  - parseCallCustomer() : 고객(리드) 초안
 *  DB에 저장하지 않는다. 실제 생성은 프론트가 확인 후 기존 엔드포인트로 진행.
 */
@Service
@RequiredArgsConstructor
public class CallParsingService {

    private static final Logger logger = LoggerFactory.getLogger(CallParsingService.class);
    private static final String ADMIN_UID = "3635939452";

    private final ClovaSpeechService clovaSpeechService;
    private final LlmExtractionService llmExtractionService;
    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;

    private void checkAuth(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
    }

    private void requirePermission(String uid, String action) {
        if (ADMIN_UID.equals(uid)) return;
        UserPermissionEntity perm = userPermissionRepository.findByUser_Uid(uid).orElse(null);
        boolean allowed;
        if (perm == null) {
            allowed = false;
        } else if ("CREATE".equals(action)) {
            allowed = Boolean.TRUE.equals(perm.getCanCreate());
        } else if ("UPDATE".equals(action)) {
            allowed = Boolean.TRUE.equals(perm.getCanUpdate());
        } else if ("DELETE".equals(action)) {
            allowed = Boolean.TRUE.equals(perm.getCanDelete());
        } else {
            allowed = false;
        }
        if (!allowed) throw new RuntimeException("해당 작업 권한이 없습니다 (" + action + ")");
    }

    // 공통: 인증 + 권한 + STT
    private String authAndTranscribe(String uid, MultipartFile audio, UserDetails userDetails) {
        checkAuth(uid, userDetails);
        requirePermission(uid, "CREATE");
        userRepository.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("오디오 파일이 없습니다");
        }
        logger.info("통화 파싱 시작 - uid={}, file={}, size={}KB",
                uid, audio.getOriginalFilename(), audio.getSize() / 1024);
        return clovaSpeechService.transcribe(audio).diarizedText();
    }

    // 계약자(임차인)/건물 초안
    public CallDraftDTO parseCall(String uid, MultipartFile audio, UserDetails userDetails) {
        String transcript = authAndTranscribe(uid, audio, userDetails);
        CallDraftDTO draft = llmExtractionService.extract(transcript);
        draft.setTranscript(transcript);
        logger.info("통화 파싱(계약자) 완료 - uid={}", uid);
        return draft;
    }

    // 고객(리드) 초안
    public CustomerDTO parseCallCustomer(String uid, MultipartFile audio, UserDetails userDetails) {
        String transcript = authAndTranscribe(uid, audio, userDetails);
        CustomerDTO draft = llmExtractionService.extractCustomer(transcript);
        // 전사 원문은 메모 뒤에 참고로 덧붙이지 않고, 프론트에서 별도 표시하지 않는다면 memo에 보존 가능.
        logger.info("통화 파싱(고객) 완료 - uid={}", uid);
        return draft;
    }
}
