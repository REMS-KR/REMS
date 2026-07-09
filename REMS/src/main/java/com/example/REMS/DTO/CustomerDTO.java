package com.example.REMS.DTO;

import com.example.REMS.Entity.CustomerEntity;
import com.example.REMS.Entity.UserEntity;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class CustomerDTO {
    private Long id;
    private String name;            // 이름 (고객명)
    private String summary;         // 요약
    private String phone;           // 전화번호
    private String sensitivity;     // 감도 (high/mid/low) — 수기 체크
    private String amount;          // 금액
    private String moveInDate;      // 입주 희망일
    private String location;        // 위치
    private String loan;            // 대출
    private String meetingDate;     // 미팅 날짜
    private String memo;            // 메모
    private String ownerUid;        // 작성자 uid (응답 표시용)
    private Long createdAt;         // 등록 일시(epoch millis)

    // 이 고객에게 연결된 매물 건물 id 목록
    @Builder.Default
    private List<Long> buildingIds = new ArrayList<>();

    public static CustomerDTO entityToDto(CustomerEntity e) {
        return CustomerDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .summary(e.getSummary())
                .phone(e.getPhone())
                .sensitivity(e.getSensitivity())
                .amount(e.getAmount())
                .moveInDate(e.getMoveInDate())
                .location(e.getLocation())
                .loan(e.getLoan())
                .meetingDate(e.getMeetingDate())
                .memo(e.getMemo())
                .ownerUid(e.getOwner() != null ? e.getOwner().getUid() : null)
                .createdAt(e.getCreatedAt() != null
                        ? e.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : null)
                .buildingIds(e.getBuildingIds() != null ? new ArrayList<>(e.getBuildingIds()) : new ArrayList<>())
                .build();
    }

    public CustomerEntity dtoToEntity(UserEntity owner) {
        return CustomerEntity.builder()
                .id(id)
                .name(name)
                .summary(summary)
                .phone(phone)
                .sensitivity(sensitivity)
                .amount(amount)
                .moveInDate(moveInDate)
                .location(location)
                .loan(loan)
                .meetingDate(meetingDate)
                .memo(memo)
                .buildingIds(buildingIds != null ? new ArrayList<>(buildingIds) : new ArrayList<>())
                .owner(owner)
                .build();
    }
}
