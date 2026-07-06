package com.example.REMS.DTO;

import com.example.REMS.Entity.CustomerEntity;
import com.example.REMS.Entity.UserEntity;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class CustomerDTO {
    private Long id;
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

    public static CustomerDTO entityToDto(CustomerEntity e) {
        return new CustomerDTO(
                e.getId(),
                e.getSummary(),
                e.getPhone(),
                e.getSensitivity(),
                e.getAmount(),
                e.getMoveInDate(),
                e.getLocation(),
                e.getLoan(),
                e.getMeetingDate(),
                e.getMemo(),
                e.getOwner() != null ? e.getOwner().getUid() : null,
                e.getCreatedAt() != null
                        ? e.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : null);
    }

    public CustomerEntity dtoToEntity(UserEntity owner) {
        return CustomerEntity.builder()
                .id(id)
                .summary(summary)
                .phone(phone)
                .sensitivity(sensitivity)
                .amount(amount)
                .moveInDate(moveInDate)
                .location(location)
                .loan(loan)
                .meetingDate(meetingDate)
                .memo(memo)
                .owner(owner)
                .build();
    }
}
