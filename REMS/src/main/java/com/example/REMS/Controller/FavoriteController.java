package com.example.REMS.Controller;

import com.example.REMS.DTO.BuildingDTO;
import com.example.REMS.Service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 찜(관심 매물) — 모든 로그인 사용자.
 *  POST /favorite/{uid}/{buildingId}  → 토글, { "favorited": true|false }
 *  GET  /favorite/ids/{uid}           → 찜한 건물 id 목록
 *  GET  /favorite/{uid}               → 찜한 건물 목록(BuildingDTO[])
 */
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "찜 토글(추가/해제)")
    @PostMapping("/{uid}/{buildingId}")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable("uid") String uid,
                                                      @PathVariable("buildingId") Long buildingId,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        boolean favorited = favoriteService.toggle(uid, buildingId, userDetails);
        return ResponseEntity.ok(Map.of("favorited", favorited, "buildingId", buildingId));
    }

    @Operation(summary = "찜한 건물 id 목록")
    @GetMapping("/ids/{uid}")
    public ResponseEntity<List<Long>> listIds(@PathVariable("uid") String uid,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(favoriteService.listIds(uid, userDetails));
    }

    @Operation(summary = "찜한 건물 목록")
    @GetMapping("/{uid}")
    public ResponseEntity<List<BuildingDTO>> listBuildings(@PathVariable("uid") String uid,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(favoriteService.listBuildings(uid, userDetails));
    }
}
