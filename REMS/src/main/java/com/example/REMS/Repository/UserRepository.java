package com.example.REMS.Repository;

import com.example.REMS.Entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUid(String uid);

    // 같은 사무소 코드를 가진 사용자들 (매물 공유 그룹)
    List<UserEntity> findByOfficeCode(String officeCode);

    Optional<UserEntity> findByNickname(String nickname);

    Optional<UserEntity> findByNicknameContainingIgnoreCase(String nickname);

    boolean existsByUid(String uid);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
