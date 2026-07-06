package com.example.REMS.Service;

import com.example.REMS.DTO.CallDraftDTO;
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
 * 통화 파싱 오케스트레이션: 인증/권한 → STT → LLM 구조화 → 초안 반환.
 *  · DB에 아무것도 저장하지 않는다. 실제 생성은 프론트가 사용자 확인 후
 *    기존 /building · /unit · /tenant 엔드포인트로 진행한다.
 *  · '초안 생성'도 CREATE 권한이 있는 사용자(중개인/관리자)만 가능하도록 제한.
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

    // 로그인 여부 검증 (다른 서비스와 동일한 패턴)
    private void checkAuth(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
    }

    // CREATE/UPDATE/DELETE 권한 — 관리자는 통과, 그 외엔 저장된 플래그로 판단
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

    public CallDraftDTO parseCall(String uid, MultipartFile audio, UserDetails userDetails) {
        checkAuth(uid, userDetails);
        requirePermission(uid, "CREATE");

        // 존재하는 사용자인지 확인
        userRepository.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("오디오 파일이 없습니다");
        }
        logger.info("통화 파싱 시작 — uid={}, file={}, size={}KB",
                uid, audio.getOriginalFilename(), audio.getSize() / 1024);

        // 1) STT (화자분리 포함)
        ClovaSpeechService.SttResult stt = clovaSpeechService.transcribe(audio);

        // 2) LLM 구조화
        CallDraftDTO draft = llmExtractionService.extract(stt.diarizedText());

        // 3) 전사 텍스트 동봉(프론트에서 원본 대조용) — DB 저장 없음
        draft.setTranscript(stt.diarizedText());

        logger.info("통화 파싱 완료 — uid={}", uid);
        return draft;
    }
}
