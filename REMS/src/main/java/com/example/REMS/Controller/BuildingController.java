package com.example.REMS.Controller;

import com.example.REMS.DTO.BuildingDTO;
import com.example.REMS.Service.BuildingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/building")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    // 건물 추가 (multipart: uid / buildingData(JSON) / mediaData(파일 여러 개, 선택))
    @SneakyThrows
    @Operation(summary = "건물 추가")
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<BuildingDTO> createBuilding(@RequestPart("uid") String uid,
                                                      @RequestPart("buildingData") String buildingData,
                                                      @RequestPart(value = "mediaData", required = false) List<MultipartFile> mediaData,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        ObjectMapper mapper = new ObjectMapper();
        BuildingDTO buildingDTO = mapper.readValue(buildingData, BuildingDTO.class);
        return ResponseEntity.ok(buildingService.createBuilding(uid, buildingDTO, mediaData, userDetails));
    }

    // 전체 건물 조회
    @Operation(summary = "전체 건물 조회")
    @GetMapping("/all/{uid}")
    public ResponseEntity<List<BuildingDTO>> getAllBuildings(@PathVariable("uid") String uid,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.getAllBuildings(uid, userDetails));
    }

    // id로 건물 조회 (호실 목록 포함)
    @Operation(summary = "id로 건물 조회")
    @GetMapping("/id/{uid}/{id}")
    public ResponseEntity<BuildingDTO> findById(@PathVariable("uid") String uid,
                                                @PathVariable("id") Long id,
                                                @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.findById(uid, id, userDetails));
    }

    // 건물명/주소 검색
    @Operation(summary = "건물명/주소 검색")
    @GetMapping("/search/{uid}")
    public ResponseEntity<List<BuildingDTO>> search(@PathVariable("uid") String uid,
                                                    @RequestParam("keyword") String keyword,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.search(uid, keyword, userDetails));
    }

    // 건물 유형별 필터
    @Operation(summary = "건물 유형별 필터")
    @GetMapping("/type/{uid}/{type}")
    public ResponseEntity<List<BuildingDTO>> findByType(@PathVariable("uid") String uid,
                                                        @PathVariable("type") String type,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.findByType(uid, type, userDetails));
    }

    // 건물 수정 (multipart: uid / buildingData(JSON, id + 유지할 mediaURLs 포함) / mediaData(새 파일 여러 개, 선택))
    @SneakyThrows
    @Operation(summary = "건물 수정")
    @PutMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<BuildingDTO> updateBuilding(@RequestPart("uid") String uid,
                                                      @RequestPart("buildingData") String buildingData,
                                                      @RequestPart(value = "mediaData", required = false) List<MultipartFile> mediaData,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        ObjectMapper mapper = new ObjectMapper();
        BuildingDTO buildingDTO = mapper.readValue(buildingData, BuildingDTO.class);
        return ResponseEntity.ok(buildingService.updateBuilding(uid, buildingDTO, mediaData, userDetails));
    }

    // 건물 삭제 (호실까지 함께 삭제)
    @Operation(summary = "건물 삭제")
    @DeleteMapping("/delete/{uid}/{id}")
    public ResponseEntity<BuildingDTO> deleteBuilding(@PathVariable("uid") String uid,
                                                      @PathVariable("id") Long id,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.deleteBuilding(uid, id, userDetails));
    }

    // 휴지통 목록 조회 (작성자 본인)
    @Operation(summary = "휴지통 목록 조회")
    @GetMapping("/trash/{uid}")
    public ResponseEntity<List<BuildingDTO>> getTrash(@PathVariable("uid") String uid,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.getTrash(uid, userDetails));
    }

    // 휴지통에서 복원 (deletedAt 해제)
    @Operation(summary = "휴지통 복원")
    @PutMapping("/restore/{uid}/{id}")
    public ResponseEntity<BuildingDTO> restoreBuilding(@PathVariable("uid") String uid,
                                                       @PathVariable("id") Long id,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.restoreBuilding(uid, id, userDetails));
    }

    // 휴지통에서 완전(영구) 삭제 — 호실까지 cascade 삭제
    @Operation(summary = "휴지통 영구 삭제")
    @DeleteMapping("/trash/{uid}/{id}")
    public ResponseEntity<BuildingDTO> permanentlyDelete(@PathVariable("uid") String uid,
                                                         @PathVariable("id") Long id,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(buildingService.permanentlyDelete(uid, id, userDetails));
    }
}
