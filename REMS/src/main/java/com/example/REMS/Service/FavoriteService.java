package com.example.REMS.Service;

import com.example.REMS.DTO.BuildingDTO;
import com.example.REMS.Entity.BuildingEntity;
import com.example.REMS.Entity.FavoriteEntity;
import com.example.REMS.Repository.BuildingRepository;
import com.example.REMS.Repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 찜(관심 매물) 서비스.
 *  · 모든 로그인 사용자가 사용 가능(권한 등급 무관) — 토큰 uid 일치만 검증.
 *  · 찜 토글 / 찜 id 목록 / 찜 건물 목록 제공.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final Logger logger = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;
    private final BuildingRepository buildingRepository;

    // 로그인 여부 + 토큰 uid 일치 검증 (다른 서비스와 동일 패턴)
    private void checkAuth(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
    }

    // 찜 토글 — 이미 찜이면 해제(false), 아니면 추가(true)
    @Transactional
    public boolean toggle(String uid, Long buildingId, UserDetails userDetails) {
        checkAuth(uid, userDetails);
        // 존재하는 건물인지 확인
        buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("건물을 찾을 수 없습니다"));

        FavoriteEntity existing = favoriteRepository.findByUidAndBuildingId(uid, buildingId).orElse(null);
        if (existing != null) {
            favoriteRepository.delete(existing);
            logger.info("찜 해제 — uid={}, buildingId={}", uid, buildingId);
            return false;
        }
        favoriteRepository.save(FavoriteEntity.builder().uid(uid).buildingId(buildingId).build());
        logger.info("찜 추가 — uid={}, buildingId={}", uid, buildingId);
        return true;
    }

    // 내 찜 건물 id 목록 (하트 상태 표시용)
    @Transactional(readOnly = true)
    public List<Long> listIds(String uid, UserDetails userDetails) {
        checkAuth(uid, userDetails);
        return favoriteRepository.findByUidOrderByCreatedAtDesc(uid).stream()
                .map(FavoriteEntity::getBuildingId)
                .collect(Collectors.toList());
    }

    // 내 찜 건물 목록 (삭제(휴지통)된 건물은 제외) — '찜' 메뉴 표시용
    @Transactional(readOnly = true)
    public List<BuildingDTO> listBuildings(String uid, UserDetails userDetails) {
        checkAuth(uid, userDetails);
        return favoriteRepository.findByUidOrderByCreatedAtDesc(uid).stream()
                .map(FavoriteEntity::getBuildingId)
                .map(id -> buildingRepository.findById(id).orElse(null))
                .filter(b -> b != null && b.getDeletedAt() == null)   // 존재 + 정상(휴지통 아님)
                .map(BuildingDTO::entityToDto)
                .collect(Collectors.toList());
    }
}
