package com.example.REMS.Repository;

import com.example.REMS.Entity.FavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<FavoriteEntity, Long> {

    // 특정 사용자의 찜 전체 (최근 찜한 순)
    List<FavoriteEntity> findByUidOrderByCreatedAtDesc(String uid);

    // 특정 사용자-건물 찜 1건
    Optional<FavoriteEntity> findByUidAndBuildingId(String uid, Long buildingId);

    boolean existsByUidAndBuildingId(String uid, Long buildingId);
}
