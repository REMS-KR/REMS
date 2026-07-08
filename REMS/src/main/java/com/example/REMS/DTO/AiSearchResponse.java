package com.example.REMS.DTO;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버 → 프론트: AI 매물 검색 결과.
 *  - criteria : 제미나이가 해석한 필터 (프론트에서 "이렇게 이해했어요" 칩으로 표시)
 *  - buildings: 그 필터로 정확히 선별된 매물 목록 (기존 BuildingDTO 그대로 → 목록 카드 재사용)
 *  - count    : 선별된 매물 수
 *  - summary  : 해석 결과를 한국어 한 줄로 요약 (서버가 결정론적으로 생성)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSearchResponse {
    private AiFilterCriteria criteria;

    @Builder.Default
    private List<BuildingDTO> buildings = new ArrayList<>();

    private int count;
    private String summary;
}
