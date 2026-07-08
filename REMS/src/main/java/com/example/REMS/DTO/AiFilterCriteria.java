package com.example.REMS.DTO;

import lombok.*;

/**
 * AI 매물 검색 — 제미나이가 사용자의 자연어를 이 구조로 "추출"한다.
 * 실제 필터링은 이 값을 받아 AiSearchService 가 코드로 정확히 수행한다.
 *
 * ★ 모든 필드는 래퍼 타입(nullable). "언급 안 된 조건 = null" 로 두어야
 *   불필요한 필터가 걸리지 않는다. (primitive boolean 을 쓰면 Jackson null 역직렬화
 *   오류가 나므로 반드시 Boolean/Integer/Double 사용)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFilterCriteria {

    // 거래유형: sale=매매 / jeonse=전세 / monthly=월세  (미지정=null → 전체)
    private String dealType;

    // 건물 유형: house/multiplex/officetel/apartment/neighborhood/commercial (미지정=null)
    private String type;

    // 금액 조건 (단위: 만원). "이하"만 있으면 Max 만, "이상"만 있으면 Min 만 채운다.
    private Integer depositMin;   // 보증금/전세금/매매가 최소
    private Integer depositMax;   // 보증금/전세금/매매가 최대
    private Integer rentMin;      // 월세 최소
    private Integer rentMax;      // 월세 최대
    private Integer manageMax;    // 관리비 최대

    // 위치 키워드 — 구/동/역/건물명 등 주소에 포함될 문자열 (예: "강남구", "역삼동")
    private String location;

    // 옵션 조건 — true 일 때만 필터 적용 (false/null 은 조건 없음으로 간주)
    private Boolean parking;      // 주차 가능
    private Boolean pet;          // 애완 가능
    private Boolean jeonseLoan;   // 전세 대출 가능
    private Boolean onlyVacant;   // 공실 있는 매물만

    // 호실 전용면적 조건 (단위: ㎡). 평으로 말하면 제미나이가 ㎡로 환산해서 넣는다.
    private Double areaMin;
    private Double areaMax;

    // 위 항목으로 못 담는 기타 자유 키워드 (건물명/메모 매칭용, 선택)
    private String keyword;
}
