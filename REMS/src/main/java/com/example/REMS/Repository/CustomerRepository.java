package com.example.REMS.Repository;

import com.example.REMS.Entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
    // 작성자 본인의 고객 목록 (최근 등록 순)
    List<CustomerEntity> findByOwner_UidOrderByIdDesc(String uid);
}
