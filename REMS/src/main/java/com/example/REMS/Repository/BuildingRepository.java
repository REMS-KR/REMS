package com.example.REMS.Repository;

import com.example.REMS.Entity.BuildingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BuildingRepository extends JpaRepository<BuildingEntity, Long> {

    // 건물 유형별 (전체 공개 조회)
    List<BuildingEntity> findByType(String type);

    // 건물명/주소 검색 (전체 공개 조회)
    @Query("SELECT b FROM buildings b WHERE (" +
           "LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.address) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND b.deletedAt IS NULL")
    List<BuildingEntity> searchAll(@Param("keyword") String keyword);

    // ===== 휴지통(소프트 삭제) 지원 =====
    // 정상(휴지통 아님) 건물만 — 지도/목록/유형필터용
    List<BuildingEntity> findByDeletedAtIsNull();
    List<BuildingEntity> findByTypeAndDeletedAtIsNull(String type);

    // 거래유형별 (sale/jeonse/monthly) — 정상 건물만
    List<BuildingEntity> findByDealTypeAndDeletedAtIsNull(String dealType);

    // 특정 사용자의 휴지통 목록 (최근 삭제 순)
    List<BuildingEntity> findByOwner_UidAndDeletedAtIsNotNullOrderByDeletedAtDesc(String uid);

    // 스케줄러: 기준 시각 이전에 휴지통으로 이동된(=30일 경과) 건물 — 영구 삭제 대상
    List<BuildingEntity> findByDeletedAtBefore(LocalDateTime threshold);
}
