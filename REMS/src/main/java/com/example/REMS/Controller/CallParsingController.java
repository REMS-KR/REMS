package com.example.REMS.Controller;

import com.example.REMS.DTO.CallDraftDTO;
import com.example.REMS.Service.CallParsingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 통화 녹음 파싱 API.
 *  POST /ai/parse-call  (multipart)
 *    - uid   : @RequestPart String
 *    - audio : @RequestPart MultipartFile (녹음 파일: m4a/mp3/wav 등)
 *  응답: CallDraftDTO (초안). 저장 X — 프론트가 확인 후 기존 생성 API로 생성.
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class CallParsingController {

    private final CallParsingService callParsingService;

    @Operation(summary = "통화 녹음 → STT → LLM 구조화 (초안 반환, 저장 안 함)")
    @PostMapping(value = "/parse-call", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CallDraftDTO> parseCall(@RequestPart("uid") String uid,
                                                  @RequestPart("audio") MultipartFile audio,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(callParsingService.parseCall(uid, audio, userDetails));
    }
}
