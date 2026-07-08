package com.example.REMS.Controller;

import com.example.REMS.DTO.AiSearchRequest;
import com.example.REMS.DTO.AiSearchResponse;
import com.example.REMS.Service.AiSearchService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * AI 매물 검색 (중개인 전용).
 *  POST /ai/search/{uid}
 *  body: { "query": "강남구 보증금 5천 이하 주차되는 월세" }
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiSearchService aiSearchService;

    @Operation(summary = "AI 자연어 매물 검색 (중개인 전용)")
    @PostMapping("/search/{uid}")
    public ResponseEntity<AiSearchResponse> search(@PathVariable("uid") String uid,
                                                   @RequestBody AiSearchRequest request,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(aiSearchService.search(uid, request.getQuery(), userDetails));
    }
}
