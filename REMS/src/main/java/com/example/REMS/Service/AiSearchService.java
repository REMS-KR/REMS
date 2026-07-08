package com.example.REMS.Service;

import com.example.REMS.DTO.AiFilterCriteria;
import com.example.REMS.DTO.AiSearchResponse;
import com.example.REMS.DTO.BuildingDTO;
import com.example.REMS.Entity.BuildingEntity;
import com.example.REMS.Entity.UnitEntity;
import com.example.REMS.Entity.UserPermissionEntity;
import com.example.REMS.Repository.BuildingRepository;
import com.example.REMS.Repository.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 매물 검색 오케스트레이션.
 *
 *  1) 중개인/관리자 권한 검증
 *  2) GeminiService 로 자연어 → 필터(AiFilterCriteria) 추출  (AI가 하는 유일한 일)
 *  3) DB의 정상 매물을 그 필터로 "코드가 정확히" 선별       (숫자/조건 = 오차 없음)
 *  4) 결과 + 해석 요약을 반환
 *
 * 필터 판단은 건물 단위 필드(거래유형/유형/금액/주소/옵션)를 기준으로 하며,
 * 지도 마커·목록의 가격 표기 로직(inferDeal)과 동일하게 맞췄다.
 */
@Service
@RequiredArgsConstructor
public class AiSearchService {

    private static final Logger logger = LoggerFactory.getLogger(AiSearchService.class);

    private final GeminiService geminiService;
    private final BuildingRepository buildingRepository;
    private final UserPermissionRepository userPermissionRepository;

    private static final String ADMIN_UID = "4979532269";

    // 로그인 여부 + 토큰 uid 일치 검증 (다른 서비스와 동일 패턴)
    private void checkAuth(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
    }

    /**
     * 중개인(또는 관리자)만 통과. 그 외에는 차단.
     *  - 이 시스템에서 '중개인·관리자' = 쓰기 권한 보유자(canCreate=true).
     *    (일반유저=조회만 → canCreate=false 이므로 자연스럽게 걸러진다)
     *  - 역할 필드로 더 엄격히 막고 싶으면 아래 canCreate 판정을
     *    "broker".equals(perm.getRole()) 로 바꾸면 된다.
     */
    private void requireBroker(String uid, UserDetails userDetails) {
        checkAuth(uid, userDetails);
        if (ADMIN_UID.equals(uid)) return;

        UserPermissionEntity perm = userPermissionRepository.findByUser_Uid(uid).orElse(null);
        boolean isBroker = (perm != null) && Boolean.TRUE.equals(perm.getCanCreate());
        if (!isBroker) {
            throw new RuntimeException("AI 매물 검색은 중개인만 사용할 수 있습니다.");
        }
    }

    @Transactional(readOnly = true)
    public AiSearchResponse search(String uid, String query, UserDetails userDetails) {
        requireBroker(uid, userDetails);

        // 1) 자연어 → 필터 (AI)
        AiFilterCriteria c = geminiService.parseQuery(query);

        // 2) 정상(휴지통 아님) 매물 전체를 코드로 선별
        List<BuildingDTO> matched = buildingRepository.findByDeletedAtIsNull().stream()
                .filter(b -> matches(b, c))
                .map(BuildingDTO::entityToDto)
                .collect(Collectors.toList());

        String summary = buildSummary(c, matched.size());
        logger.info("AI 검색 완료! 요청자: {}, query=\"{}\", 결과 {}건", uid, query, matched.size());

        return AiSearchResponse.builder()
                .criteria(c)
                .buildings(matched)
                .count(matched.size())
                .summary(summary)
                .build();
    }

    // ============================================================
    // 결정론적 필터 — 모든 조건은 AND. 값이 null 이면 그 조건은 건너뜀.
    // ============================================================
    private boolean matches(BuildingEntity b, AiFilterCriteria c) {
        // 거래유형
        if (c.getDealType() != null && !c.getDealType().equals(inferDeal(b))) return false;

        // 건물 유형
        if (c.getType() != null && !c.getType().equalsIgnoreCase(safe(b.getType()))) return false;

        // 금액 (만원)
        int deposit = b.getDeposit();
        int rent = b.getRent();
        int manage = b.getManage();
        if (c.getDepositMin() != null && deposit < c.getDepositMin()) return false;
        if (c.getDepositMax() != null && deposit > c.getDepositMax()) return false;
        if (c.getRentMin() != null && rent < c.getRentMin()) return false;
        if (c.getRentMax() != null && rent > c.getRentMax()) return false;
        if (c.getManageMax() != null && manage > c.getManageMax()) return false;

        // 위치 — 주소 또는 건물명에 키워드 포함
        if (c.getLocation() != null && !c.getLocation().isBlank()) {
            String key = c.getLocation().trim().toLowerCase();
            String hay = (safe(b.getAddress()) + " " + safe(b.getDetailAddress()) + " " + safe(b.getName())).toLowerCase();
            if (!hay.contains(key)) return false;
        }

        // 옵션 (true 로 요구된 경우만 검사)
        if (Boolean.TRUE.equals(c.getParking()) && !Boolean.TRUE.equals(b.getParkingAvailable())) return false;
        if (Boolean.TRUE.equals(c.getPet()) && !Boolean.TRUE.equals(b.getPetAllowed())) return false;
        if (Boolean.TRUE.equals(c.getJeonseLoan()) && !Boolean.TRUE.equals(b.getJeonseLoanAvailable())) return false;

        // 공실만 — 호실 중 하나라도 '공실'이면 통과 (만기 지난 임차중도 공실로 간주)
        if (Boolean.TRUE.equals(c.getOnlyVacant()) && !hasVacantUnit(b)) return false;

        // 면적 — 호실 중 하나라도 범위에 들면 통과
        if (c.getAreaMin() != null || c.getAreaMax() != null) {
            boolean anyAreaMatch = b.getUnits().stream().anyMatch(u -> {
                double a = u.getArea();
                if (c.getAreaMin() != null && a < c.getAreaMin()) return false;
                if (c.getAreaMax() != null && a > c.getAreaMax()) return false;
                return true;
            });
            if (!anyAreaMatch) return false;
        }

        // 기타 키워드 — 건물명/메모/주소에 포함
        if (c.getKeyword() != null && !c.getKeyword().isBlank()) {
            String key = c.getKeyword().trim().toLowerCase();
            String hay = (safe(b.getName()) + " " + safe(b.getMemo()) + " " + safe(b.getAddress())).toLowerCase();
            if (!hay.contains(key)) return false;
        }

        return true;
    }

    // 거래유형 결정 — 프론트 inferDeal 과 동일 규칙
    private String inferDeal(BuildingEntity b) {
        String d = b.getDealType();
        if ("sale".equals(d) || "jeonse".equals(d) || "monthly".equals(d)) return d;
        if (b.getRent() > 0) return "monthly";
        if (b.getDeposit() > 0) return "jeonse";
        return "monthly";
    }

    // 호실 하나라도 공실인지 (만기일이 지난 임차중/만기임박도 공실로 간주 — 프론트 effectiveStatus 와 동일)
    private boolean hasVacantUnit(BuildingEntity b) {
        LocalDate today = LocalDate.now();
        for (UnitEntity u : b.getUnits()) {
            if (isEffectivelyVacant(u, today)) return true;
        }
        return false;
    }

    private boolean isEffectivelyVacant(UnitEntity u, LocalDate today) {
        if (u == null) return false;
        if ("empty".equals(u.getStatus())) return true;
        String end = u.getContractEnd();
        if (end != null && !end.isBlank()) {
            try {
                LocalDate e = LocalDate.parse(end.trim());   // yyyy-MM-dd
                if (!e.isAfter(today)) return true;           // 만기일 당일 포함 → 공실
            } catch (Exception ignore) { /* 형식 이상값은 무시 */ }
        }
        return false;
    }

    private String safe(String s) { return s == null ? "" : s; }

    // ============================================================
    // 해석 요약 — 프론트 "이렇게 이해했어요" 표시용 (결정론적 생성)
    // ============================================================
    private static final java.util.Map<String, String> DEAL_LABEL = java.util.Map.of(
            "sale", "매매", "jeonse", "전세", "monthly", "월세");
    private static final java.util.Map<String, String> TYPE_LABEL = java.util.Map.of(
            "house", "단독·다중", "multiplex", "다세대", "officetel", "오피스텔",
            "apartment", "아파트", "neighborhood", "근린생활", "commercial", "상가");

    private String buildSummary(AiFilterCriteria c, int count) {
        List<String> parts = new ArrayList<>();
        if (c.getDealType() != null) parts.add(DEAL_LABEL.getOrDefault(c.getDealType(), c.getDealType()));
        if (c.getType() != null) parts.add(TYPE_LABEL.getOrDefault(c.getType(), c.getType()));
        if (c.getLocation() != null && !c.getLocation().isBlank()) parts.add(c.getLocation().trim());

        addRange(parts, "보증금", c.getDepositMin(), c.getDepositMax(), "만원");
        addRange(parts, "월세", c.getRentMin(), c.getRentMax(), "만원");
        if (c.getManageMax() != null) parts.add("관리비 " + fmt(c.getManageMax()) + "만원 이하");
        addRangeD(parts, "면적", c.getAreaMin(), c.getAreaMax(), "㎡");

        if (Boolean.TRUE.equals(c.getParking())) parts.add("주차 가능");
        if (Boolean.TRUE.equals(c.getPet())) parts.add("애완 가능");
        if (Boolean.TRUE.equals(c.getJeonseLoan())) parts.add("전세대출 가능");
        if (Boolean.TRUE.equals(c.getOnlyVacant())) parts.add("공실");
        if (c.getKeyword() != null && !c.getKeyword().isBlank()) parts.add(c.getKeyword().trim());

        String cond = parts.isEmpty() ? "전체 조건" : String.join(" · ", parts);
        return cond + " → " + count + "건";
    }

    private void addRange(List<String> parts, String label, Integer min, Integer max, String unit) {
        if (min != null && max != null) parts.add(label + " " + fmt(min) + "~" + fmt(max) + unit);
        else if (max != null) parts.add(label + " " + fmt(max) + unit + " 이하");
        else if (min != null) parts.add(label + " " + fmt(min) + unit + " 이상");
    }

    private void addRangeD(List<String> parts, String label, Double min, Double max, String unit) {
        if (min != null && max != null) parts.add(label + " " + fmtD(min) + "~" + fmtD(max) + unit);
        else if (max != null) parts.add(label + " " + fmtD(max) + unit + " 이하");
        else if (min != null) parts.add(label + " " + fmtD(min) + unit + " 이상");
    }

    private String fmt(int manwon) {
        if (manwon >= 10000) {
            double eok = Math.round((manwon / 10000.0) * 10) / 10.0;
            String e = (eok == Math.floor(eok)) ? String.valueOf((long) eok) : String.valueOf(eok);
            return e + "억";
        }
        return String.format("%,d", manwon);
    }

    private String fmtD(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
