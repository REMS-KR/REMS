package com.example.REMS.DTO;

import lombok.*;

import java.util.*;

/**
 * 통화 녹음 → STT → LLM 구조화 결과를 담는 "초안" DTO.
 *  · DB에 저장하지 않는다. 프론트에서 사용자 확인/수정 후 기존 생성 API로 넘긴다.
 *  · building / units / tenant 는 기존 DTO를 그대로 재사용 → 확정 시 그대로 생성 API에 전달 가능.
 *  · confidence: 각 항목의 신뢰도(0.0~1.0). 프론트에서 낮으면 강조 표시.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class CallDraftDTO {

    // 화자분리된 전사 텍스트 (사용자가 원본과 대조할 수 있게 반환)
    private String transcript;

    // 추출된 건물 초안 (units 는 아래 top-level units 사용, building.units 는 비움)
    private BuildingDTO building;

    // 추출된 호실 초안 목록
    @Builder.Default
    private List<UnitDTO> units = new ArrayList<>();

    // 추출된 계약자 초안 (중개사만 사용). 통화에 계약자 정보가 없으면 null.
    private TenantDTO tenant;

    // 항목별 신뢰도 (building/units/tenant → 0.0~1.0)
    @Builder.Default
    private Map<String, Double> confidence = new HashMap<>();
}
