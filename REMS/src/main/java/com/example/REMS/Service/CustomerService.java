package com.example.REMS.Service;

import com.example.REMS.DTO.CustomerDTO;
import com.example.REMS.Entity.CustomerEntity;
import com.example.REMS.Entity.UserEntity;
import com.example.REMS.Entity.UserPermissionEntity;
import com.example.REMS.Repository.CustomerRepository;
import com.example.REMS.Repository.UserPermissionRepository;
import com.example.REMS.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);
    private static final String ADMIN_UID = "4979532269";

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final com.example.REMS.Repository.BuildingRepository buildingRepository;

    // 토큰 검증 후 사용자 반환
    private UserEntity getAuthorizedUser(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        return userRepository.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private boolean isBroker(UserEntity user) {
        if (ADMIN_UID.equals(user.getUid())) return true;
        UserPermissionEntity perm = userPermissionRepository.findByUser_Uid(user.getUid()).orElse(null);
        if (perm != null && perm.getRole() != null) {
            return "broker".equals(perm.getRole());
        }
        return user.getAgencyName() != null && !user.getAgencyName().trim().isEmpty();
    }

    private void requireBroker(UserEntity user) {
        if (!isBroker(user)) {
            throw new RuntimeException("중개사 회원만 이용할 수 있는 기능입니다");
        }
    }

    private void requirePermission(String uid, String action) {
        if (ADMIN_UID.equals(uid)) return;
        UserPermissionEntity perm = userPermissionRepository.findByUser_Uid(uid).orElse(null);
        boolean allowed;
        if (perm == null) {
            allowed = false;
        } else if ("CREATE".equals(action)) {
            allowed = Boolean.TRUE.equals(perm.getCanCreate());
        } else if ("UPDATE".equals(action)) {
            allowed = Boolean.TRUE.equals(perm.getCanUpdate());
        } else if ("DELETE".equals(action)) {
            allowed = Boolean.TRUE.equals(perm.getCanDelete());
        } else {
            allowed = false;
        }
        if (!allowed) throw new RuntimeException("해당 작업 권한이 없습니다 (" + action + ")");
    }

    // 고객 등록 (본인 소유)
    @Transactional
    public CustomerDTO createCustomer(String uid, CustomerDTO dto, UserDetails userDetails) {
        UserEntity owner = getAuthorizedUser(uid, userDetails);
        requireBroker(owner);
        requirePermission(uid, "CREATE");
        CustomerEntity saved = customerRepository.save(dto.dtoToEntity(owner));
        logger.info("고객 등록 완료! 작성자: {}, id={}", uid, saved.getId());
        return CustomerDTO.entityToDto(saved);
    }

    // 내 고객 목록
    @Transactional(readOnly = true)
    public List<CustomerDTO> getMyCustomers(String uid, UserDetails userDetails) {
        UserEntity owner = getAuthorizedUser(uid, userDetails);
        requireBroker(owner);
        return customerRepository.findByOwner_UidOrderByIdDesc(uid).stream()
                .map(CustomerDTO::entityToDto)
                .collect(Collectors.toList());
    }

    // 고객 수정 (본인 것만)
    @Transactional
    public CustomerDTO updateCustomer(String uid, Long id, CustomerDTO dto, UserDetails userDetails) {
        UserEntity owner = getAuthorizedUser(uid, userDetails);
        requireBroker(owner);
        requirePermission(uid, "UPDATE");
        CustomerEntity e = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("고객을 찾을 수 없습니다"));
        if (e.getOwner() == null || !e.getOwner().getUid().equals(uid)) {
            throw new RuntimeException("본인이 등록한 고객만 수정할 수 있습니다");
        }
        e.setName(dto.getName());
        e.setSummary(dto.getSummary());
        e.setPhone(dto.getPhone());
        e.setSensitivity(dto.getSensitivity());
        e.setAmount(dto.getAmount());
        e.setMoveInDate(dto.getMoveInDate());
        e.setLocation(dto.getLocation());
        e.setLoan(dto.getLoan());
        e.setMeetingDate(dto.getMeetingDate());
        e.setMemo(dto.getMemo());
        // 알림용 일정 — 값이 바뀌면 재발송을 위해 플래그 초기화
        java.time.LocalDateTime nMeet = CustomerDTO.parseDateTime(dto.getMeetingAt());
        if (!java.util.Objects.equals(nMeet, e.getMeetingAt())) { e.setMeetingAt(nMeet); e.setNotifiedMeeting(false); e.setNotifiedMeeting2(false); }
        java.time.LocalDateTime nCon = CustomerDTO.parseDateTime(dto.getContractAt());
        if (!java.util.Objects.equals(nCon, e.getContractAt())) { e.setContractAt(nCon); e.setNotifiedContract(false); e.setNotifiedContract2(false); }
        java.time.LocalDate nBal = CustomerDTO.parseDate(dto.getBalanceOn());
        if (!java.util.Objects.equals(nBal, e.getBalanceOn())) { e.setBalanceOn(nBal); e.setNotifiedBalance(false); e.setNotifiedBalance2(false); }
        java.time.LocalDate nMove = CustomerDTO.parseDate(dto.getMoveInOn());
        if (!java.util.Objects.equals(nMove, e.getMoveInOn())) { e.setMoveInOn(nMove); e.setNotifiedMoveIn(false); e.setNotifiedMoveIn2(false); }
        logger.info("고객 수정 완료! 작성자: {}, id={}", uid, id);
        return CustomerDTO.entityToDto(e);
    }

    // 고객에게 매물(건물) 연결 (본인 고객만)
    @Transactional
    public CustomerDTO attachBuilding(String uid, Long id, Long buildingId, UserDetails userDetails) {
        UserEntity owner = getAuthorizedUser(uid, userDetails);
        requireBroker(owner);
        requirePermission(uid, "UPDATE");
        CustomerEntity e = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("고객을 찾을 수 없습니다"));
        if (e.getOwner() == null || !e.getOwner().getUid().equals(uid)) {
            throw new RuntimeException("본인이 등록한 고객만 수정할 수 있습니다");
        }
        if (!buildingRepository.existsById(buildingId)) {
            throw new IllegalArgumentException("매물을 찾을 수 없습니다");
        }
        if (e.getBuildingIds() == null) e.setBuildingIds(new java.util.ArrayList<>());
        if (!e.getBuildingIds().contains(buildingId)) {
            e.getBuildingIds().add(buildingId);
        }
        logger.info("고객({})에 매물({}) 연결! 작성자: {}", id, buildingId, uid);
        return CustomerDTO.entityToDto(e);
    }

    // 고객에서 매물(건물) 연결 해제 (본인 고객만)
    @Transactional
    public CustomerDTO detachBuilding(String uid, Long id, Long buildingId, UserDetails userDetails) {
        UserEntity owner = getAuthorizedUser(uid, userDetails);
        requireBroker(owner);
        requirePermission(uid, "UPDATE");
        CustomerEntity e = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("고객을 찾을 수 없습니다"));
        if (e.getOwner() == null || !e.getOwner().getUid().equals(uid)) {
            throw new RuntimeException("본인이 등록한 고객만 수정할 수 있습니다");
        }
        if (e.getBuildingIds() != null) {
            e.getBuildingIds().remove(buildingId);
        }
        logger.info("고객({})에서 매물({}) 연결 해제! 작성자: {}", id, buildingId, uid);
        return CustomerDTO.entityToDto(e);
    }

    // 고객 삭제 (본인 것만)
    @Transactional
    public CustomerDTO deleteCustomer(String uid, Long id, UserDetails userDetails) {
        UserEntity owner = getAuthorizedUser(uid, userDetails);
        requireBroker(owner);
        requirePermission(uid, "DELETE");
        CustomerEntity e = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("고객을 찾을 수 없습니다"));
        if (e.getOwner() == null || !e.getOwner().getUid().equals(uid)) {
            throw new RuntimeException("본인이 등록한 고객만 삭제할 수 있습니다");
        }
        CustomerDTO deleted = CustomerDTO.entityToDto(e);
        customerRepository.delete(e);
        logger.info("고객 삭제 완료! 작성자: {}, id={}", uid, id);
        return deleted;
    }
}
