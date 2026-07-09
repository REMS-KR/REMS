package com.example.REMS.Controller;

import com.example.REMS.DTO.CallDraftDTO;
import com.example.REMS.DTO.CustomerDTO;
import com.example.REMS.Service.CallParsingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 통화 녹음 파싱 API (저장 X - 프론트가 확인 후 기존 생성 API로 생성).
 *  POST /ai/parse-call           (multipart: uid, audio) -> 계약자/건물 초안(CallDraftDTO)
 *  POST /ai/parse-call/customer  (multipart: uid, audio) -> 고객 초안(CustomerDTO)
 *
 *  ※ STT/LLM 기능 오류(예: CLOVA 무료 인식 시간 초과)는 500 이 아니라 422 로 내려
 *    프론트의 "500 → 자동 로그아웃" 규칙에 걸리지 않게 하고, CLOVA 메시지를 그대로 전달한다.
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class CallParsingController {

    private static final Logger logger = LoggerFactory.getLogger(CallParsingController.class);

    private final CallParsingService callParsingService;

    @Operation(summary = "통화 녹음 -> 계약자/건물 초안")
    @PostMapping(value = "/parse-call", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> parseCall(@RequestPart("uid") String uid,
                                       @RequestPart("audio") MultipartFile audio,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(callParsingService.parseCall(uid, audio, userDetails));
        } catch (RuntimeException e) {
            return functionError(e);
        }
    }

    @Operation(summary = "통화 녹음 -> 고객 초안")
    @PostMapping(value = "/parse-call/customer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> parseCallCustomer(@RequestPart("uid") String uid,
                                               @RequestPart("audio") MultipartFile audio,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(callParsingService.parseCallCustomer(uid, audio, userDetails));
        } catch (RuntimeException e) {
            return functionError(e);
        }
    }

    // 기능 오류는 422(Unprocessable) + 메시지로 — 로그아웃 유발(500) 방지, 사용자에게 원인 그대로 노출
    private ResponseEntity<Map<String, Object>> functionError(RuntimeException e) {
        String msg = (e.getMessage() != null && !e.getMessage().isBlank())
                ? e.getMessage() : "통화 분석에 실패했습니다.";
        logger.warn("통화 파싱 기능 오류(422): {}", msg);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("message", msg));
    }
}
