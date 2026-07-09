package com.example.REMS.DTO;

import com.example.REMS.Entity.TenantEntity;
import com.example.REMS.Entity.UserEntity;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TenantDTO {
    private Long id;
    private String name;            // 이름 (계약자명)
    private String phone;
    private String buildingName;
    private String unitName;
    private int deposit;
    private int rent;
    private int manage;
    private String contractStart;
    private String contractEnd;
    private String ownerUid;        // 작성자 uid (응답 표시용)
    private Long createdAt;         // 등록 일시(epoch millis)

    public static TenantDTO entityToDto(TenantEntity e) {
        return TenantDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .phone(e.getPhone())
                .buildingName(e.getBuildingName())
                .unitName(e.getUnitName())
                .deposit(e.getDeposit())
                .rent(e.getRent())
                .manage(e.getManage())
                .contractStart(e.getContractStart())
                .contractEnd(e.getContractEnd())
                .ownerUid(e.getOwner() != null ? e.getOwner().getUid() : null)
                .createdAt(e.getCreatedAt() != null
                        ? e.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : null)
                .build();
    }

    public TenantEntity dtoToEntity(UserEntity owner) {
        return TenantEntity.builder()
                .id(id)
                .name(name)
                .phone(phone)
                .buildingName(buildingName)
                .unitName(unitName)
                .deposit(deposit)
                .rent(rent)
                .manage(manage)
                .contractStart(contractStart)
                .contractEnd(contractEnd)
                .owner(owner)
                .build();
    }
}
