package com.example.REMS.DTO;

import lombok.*;

/**
 * 프론트 → 서버: AI 매물 검색 요청.
 *  query 예) "강남구에서 보증금 5천 이하, 주차 되는 월세 매물 찾아줘"
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSearchRequest {
    private String query;   // 사용자가 입력한 자연어 문장
}
