package com.example.REMS.Service;

import com.example.REMS.Config.OAuthProperties.GoogleOAuthProperties;
import com.example.REMS.Config.OAuthProperties.KakaoOAuthProperties;
import com.example.REMS.Config.OAuthProperties.NaverOAuthProperties;
import com.example.REMS.Repository.UserRepository;
import com.example.REMS.Config.JWT.JwtTokenProvider;
import com.example.REMS.Config.OAuthProperties.*;
import com.example.REMS.DTO.JWTDTO;
import com.example.REMS.DTO.UserDTO;
import com.example.REMS.DTO.UserPermissionDTO;
import com.example.REMS.Entity.*;
import com.example.REMS.Repository.*;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private final KakaoOAuthProperties kakaoOAuthProperties;
    private final NaverOAuthProperties naverOAuthProperties;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final Storage storage;
    @Value("${google.cloud.credentials.header}")
    private String googleCouldHeader;

    // uid 중복 확인
    public boolean isUidDuplication(String uid) {
        return userRepository.existsByUid(uid);
    }

    // nickname 중복 확인
    public boolean isNicknameDuplication(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    // email 중복 확인
    public boolean isEmailDuplication(String email) {
        return userRepository.existsByEmail(email);
    }

    // phone 중복 확인
    public boolean isPhoneDuplication(String phone) {
        return userRepository.existsByPhone(phone);
    }

    // 회원 가입
    public UserDTO createUser(UserDTO userDTO) {
        if (isUidDuplication(userDTO.getUid())) {
            throw new IllegalArgumentException("중복된 아이디가 존재합니다");
        } else if (isNicknameDuplication(userDTO.getNickname())) {
            throw new IllegalArgumentException("중복된 닉네임이 존재합니다");
        } else if (isEmailDuplication(userDTO.getEmail())) {
            throw new IllegalArgumentException("중복된 이메일이 존재합니다");
        } else if (isPhoneDuplication(userDTO.getPhone())) {
            throw new IllegalArgumentException("중복된 휴대폰 번호가 존재합니다");
        }
        UserEntity userEntity = userDTO.dtoToEntity();
        userEntity.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        userEntity.setProvider("normal");
        UserEntity savedUser = userRepository.save(userEntity);
        logger.info("회원가입 완료!");
        return UserDTO.entityToDto(savedUser);
    }

    // 로그인
    public JWTDTO login(String uid, String password) {
        UserEntity userEntity = userRepository.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        if (!passwordEncoder.matches(password, userEntity.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }
        String token = jwtTokenProvider.generateToken(uid);
        logger.info("로그인 성공! 새로운 토큰이 발급되었습니다");
        return new JWTDTO(token, UserDTO.entityToDto(userEntity));
    }

    // 전체 회원 조회
    public List<UserDTO> getAllUsers(String uid, UserDetails userDetails) {
        if (!userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        List<UserDTO> userDTO = userRepository.findAll().stream()
                .map(UserDTO::entityToDto)
                .collect(Collectors.toList());
        logger.info(userDTO.size() + "명 사용자 전체 조회 완료!");
        return userDTO;
    }

    // id로 회원 조회
    public UserDTO findById(Long id, String uid, UserDetails userDetails) {
        if (!userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        UserEntity userEntity = userRepository.findById(id).orElseThrow();
        logger.info(id + "번 유저 조회 완료!");
        return UserDTO.entityToDto(userEntity);
    }

    // 자기 자신 조회
    public UserDTO findByUid(String uid, UserDetails userDetails) {
        if (!userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        UserEntity userEntity = userRepository.findByUid(uid).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        logger.info(uid + " 유저 조회 완료!");
        return UserDTO.entityToDto(userEntity);
    }

    // =====================================================
    // 권한 관리 (관리자 전용) — 모든 오브젝트 생성/조회/수정/삭제 허용 여부
    // =====================================================
    private static final String ADMIN_UID = "4979532269";   // 관리자 uid (고정)

    public boolean isAdmin(String uid) {
        return ADMIN_UID.equals(uid);
    }

    // 역할 상수
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_BROKER = "broker";
    public static final String ROLE_REGULAR = "regular";

    // 역할 → CRUD 플래그 동기화 (일반=조회만 / 중개인·관리자=전부)
    private void applyRole(UserPermissionEntity perm, String role) {
        boolean full = ROLE_ADMIN.equals(role) || ROLE_BROKER.equals(role);
        perm.setRole(role);
        perm.setCanCreate(full);
        perm.setCanRead(true);
        perm.setCanUpdate(full);
        perm.setCanDelete(full);
    }

    // 권한 엔티티 조회(없으면 역할 기본값으로 생성). 관리자 uid는 항상 admin 역할로 강제.
    private UserPermissionEntity getOrCreatePermission(UserEntity user) {
        UserPermissionEntity perm = userPermissionRepository.findByUser_Id(user.getId()).orElse(null);
        if (perm == null) {
            // 기본 역할: 관리자 uid → admin, 사무소 정보 보유 → broker, 그 외 → regular
            String role = isAdmin(user.getUid()) ? ROLE_ADMIN
                    : (user.getAgencyName() != null && !user.getAgencyName().trim().isEmpty() ? ROLE_BROKER : ROLE_REGULAR);
            perm = UserPermissionEntity.builder().user(user).build();
            applyRole(perm, role);
            perm = userPermissionRepository.save(perm);
        }
        // 관리자 uid는 언제나 admin 역할 보장
        if (isAdmin(user.getUid()) && !ROLE_ADMIN.equals(perm.getRole())) {
            applyRole(perm, ROLE_ADMIN);
            userPermissionRepository.save(perm);
        }
        return perm;
    }

    private UserPermissionDTO toPermissionDTO(UserEntity user, UserPermissionEntity perm) {
        boolean admin = isAdmin(user.getUid());
        String role = admin ? ROLE_ADMIN : (perm.getRole() != null ? perm.getRole() : ROLE_REGULAR);
        boolean full = ROLE_ADMIN.equals(role) || ROLE_BROKER.equals(role);
        return UserPermissionDTO.builder()
                .userId(user.getId())
                .uid(user.getUid())
                .name(user.getName())
                .nickname(user.getNickname())
                .profileURL(user.getProfileURL())
                .admin(admin)
                .role(role)
                // 역할에서 파생: 일반=조회만, 중개인·관리자=전부
                .canCreate(full)
                .canRead(true)
                .canUpdate(full)
                .canDelete(full)
                .build();
    }

    // 로그인한 유저 본인의 권한 조회 (프론트 버튼/조회 게이팅용)
    @org.springframework.transaction.annotation.Transactional
    public UserPermissionDTO getMyPermission(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        UserEntity user = userRepository.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        return toPermissionDTO(user, getOrCreatePermission(user));
    }

    // ========================================================
    // 사무소 공유 (같은 코드를 가진 중개사끼리 매물 co-관리)
    // ========================================================
    private static final String OFFICE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 헷갈리는 0/O/1/I 제외

    private UserEntity authUser(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        return userRepository.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private void requireBrokerUser(UserEntity user) {
        String role = isAdmin(user.getUid()) ? ROLE_ADMIN : getOrCreatePermission(user).getRole();
        if (!(ROLE_BROKER.equals(role) || ROLE_ADMIN.equals(role))) {
            throw new RuntimeException("중개사 회원만 사용할 수 있는 기능입니다");
        }
    }

    private String randomOfficeCode(int n) {
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(OFFICE_CODE_CHARS.charAt(r.nextInt(OFFICE_CODE_CHARS.length())));
        return sb.toString();
    }

    private String genUniqueOfficeCode() {
        for (int i = 0; i < 30; i++) {
            String c = randomOfficeCode(6);
            if (userRepository.findByOfficeCode(c).isEmpty()) return c;
        }
        return randomOfficeCode(8);
    }

    private List<Map<String, Object>> officeMembers(String code) {
        if (code == null || code.isBlank()) return List.of();
        return userRepository.findByOfficeCode(code).stream().map(u -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("uid", u.getUid());
            m.put("name", u.getName());
            m.put("nickname", u.getNickname());
            m.put("profileURL", u.getProfileURL());
            m.put("agencyName", u.getAgencyName());
            return m;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> officeInfo(String code) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("code", (code != null && !code.isBlank()) ? code : null);
        m.put("members", officeMembers(code));
        return m;
    }

    // 같은 사무소(공유 그룹) 소속 uid 목록 — 미소속이면 본인만. (BuildingService 등에서 사용)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<String> officeMemberUids(String uid) {
        UserEntity u = userRepository.findByUid(uid).orElse(null);
        if (u == null) return List.of(uid);
        String code = u.getOfficeCode();
        if (code == null || code.isBlank()) return List.of(uid);
        List<String> uids = userRepository.findByOfficeCode(code).stream()
                .map(UserEntity::getUid).collect(Collectors.toList());
        return uids.isEmpty() ? List.of(uid) : uids;
    }

    // 두 uid 가 같은 사무소 그룹인지
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean sameOffice(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        UserEntity ua = userRepository.findByUid(a).orElse(null);
        UserEntity ub = userRepository.findByUid(b).orElse(null);
        if (ua == null || ub == null) return false;
        String ca = ua.getOfficeCode();
        return ca != null && !ca.isBlank() && ca.equals(ub.getOfficeCode());
    }

    // 사무소 코드 생성(없으면) 또는 기존 코드 반환 (중개사 전용)
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> createOrGetOfficeCode(String uid, UserDetails userDetails) {
        UserEntity u = authUser(uid, userDetails);
        requireBrokerUser(u);
        if (u.getOfficeCode() == null || u.getOfficeCode().isBlank()) {
            u.setOfficeCode(genUniqueOfficeCode());
            userRepository.save(u);
            logger.info("사무소 코드 생성 - uid={}, code={}", uid, u.getOfficeCode());
        }
        return officeInfo(u.getOfficeCode());
    }

    // 기존 사무소 코드로 참여 (중개사 전용)
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> joinOffice(String uid, String code, UserDetails userDetails) {
        UserEntity u = authUser(uid, userDetails);
        requireBrokerUser(u);
        if (code == null || code.trim().isBlank()) throw new IllegalArgumentException("사무소 코드를 입력하세요");
        String c = code.trim().toUpperCase();
        if (userRepository.findByOfficeCode(c).isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 사무소 코드입니다");
        }
        u.setOfficeCode(c);
        userRepository.save(u);
        logger.info("사무소 참여 - uid={}, code={}", uid, c);
        return officeInfo(c);
    }

    // 사무소 나가기 (코드 해제)
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> leaveOffice(String uid, UserDetails userDetails) {
        UserEntity u = authUser(uid, userDetails);
        u.setOfficeCode(null);
        userRepository.save(u);
        logger.info("사무소 나가기 - uid={}", uid);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("code", null);
        m.put("members", List.of());
        return m;
    }

    // 내 사무소 정보(코드 + 멤버)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> getOffice(String uid, UserDetails userDetails) {
        UserEntity u = authUser(uid, userDetails);
        return officeInfo(u.getOfficeCode());
    }

    // 관리자: 전체 유저 + 권한 목록
    @org.springframework.transaction.annotation.Transactional
    public List<UserPermissionDTO> getAllPermissions(String uid, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        if (!isAdmin(uid)) {
            throw new RuntimeException("관리자만 접근할 수 있습니다");
        }
        List<UserPermissionDTO> result = new java.util.ArrayList<>();
        for (UserEntity user : userRepository.findAll()) {
            result.add(toPermissionDTO(user, getOrCreatePermission(user)));
        }
        logger.info("권한 목록 {}명 조회 완료! (관리자: {})", result.size(), uid);
        return result;
    }

    // 관리자: 특정 유저 권한 수정
    @org.springframework.transaction.annotation.Transactional
    public UserPermissionDTO updatePermission(String uid, Long targetUserId, UserPermissionDTO dto, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        if (!isAdmin(uid)) {
            throw new RuntimeException("관리자만 권한을 변경할 수 있습니다");
        }
        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다"));
        UserPermissionEntity perm = getOrCreatePermission(target);

        // 관리자 대상은 역할 변경 불가 (고정)
        if (isAdmin(target.getUid())) {
            return toPermissionDTO(target, perm);
        }

        // 역할 결정: dto.role 우선, 없으면(구버전 호환) CRUD 플래그로 추정
        String role = dto.getRole();
        if (role == null) {
            boolean full = Boolean.TRUE.equals(dto.getCanCreate())
                    || Boolean.TRUE.equals(dto.getCanUpdate())
                    || Boolean.TRUE.equals(dto.getCanDelete());
            role = full ? ROLE_BROKER : ROLE_REGULAR;
        }
        if (!ROLE_BROKER.equals(role) && !ROLE_REGULAR.equals(role)) role = ROLE_REGULAR;

        applyRole(perm, role);
        userPermissionRepository.save(perm);
        logger.info("{}번 유저 역할 변경 완료! role={}, (관리자: {})", targetUserId, role, uid);
        return toPermissionDTO(target, perm);
    }

    // 회원 수정 (닉네임/프로필 등 부분 업데이트, 새 이미지가 없으면 기존 프로필 그대로 유지)
    public UserDTO updateUser(UserDTO userDTO, MultipartFile mediaFile, UserDetails userDetails) {
        if (userDetails == null || !userDetails.getUsername().equals(userDTO.getUid())) {
            throw new RuntimeException("권한이 없습니다");
        }
        UserEntity userEntity = userRepository.findByUid(userDTO.getUid())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        // 전달된 값만 갱신 (null 이면 기존 값 유지 → 사진만 바꿔도 다른 정보가 지워지지 않음)
        if (userDTO.getName() != null) userEntity.setName(userDTO.getName());
        if (userDTO.getNickname() != null) userEntity.setNickname(userDTO.getNickname());
        if (userDTO.getEmail() != null) userEntity.setEmail(userDTO.getEmail());
        if (userDTO.getPhone() != null) userEntity.setPhone(userDTO.getPhone());
        if (userDTO.getAddress() != null) userEntity.setAddress(userDTO.getAddress());
        if (userDTO.getGender() != null) userEntity.setGender(userDTO.getGender());
        // [B] edit by smsong - 공인중개사사무소 정보 부분 업데이트(전달된 값만 갱신)
        if (userDTO.getAgencyName() != null) userEntity.setAgencyName(userDTO.getAgencyName());
        if (userDTO.getAgencyPhone() != null) userEntity.setAgencyPhone(userDTO.getAgencyPhone());
        if (userDTO.getAgencyAddress() != null) userEntity.setAgencyAddress(userDTO.getAgencyAddress());
        // [E] edit by smsong
        userEntity.setAge(userDTO.getAge());

        // ★ 핵심 수정: 새 이미지가 있을 때만 프로필 교체. 없으면 기존 프로필 URL 유지.
        //   (기존 코드는 새 파일이 없으면 profileURL 을 null 로 덮어써서 프로필 사진이 사라지는 버그가 있었음)
        if (mediaFile != null && !mediaFile.isEmpty()) {
            userEntity.setProfileURL(uploadProfileImage(mediaFile));
        } else if (userDTO.getProfileURL() != null && userDTO.getProfileURL().isEmpty()) {
            // profileURL 을 빈 문자열("")로 명시해 보내면 프로필 제거 (기존 GCS 파일도 best-effort 정리)
            deleteProfileImageQuietly(userEntity.getProfileURL());
            userEntity.setProfileURL(null);
        }
        // (그 외: 새 이미지도 없고 profileURL 도 안 보냈으면 기존 프로필 그대로 유지)

        UserEntity updatedUser = userRepository.save(userEntity);
        logger.info(userEntity.getId() + "번 사용자 정보 업데이트 완료!");
        return UserDTO.entityToDto(updatedUser);
    }

    // 프로필 이미지 GCS 업로드 (UUID 파일명으로 충돌 방지)
    private String uploadProfileImage(MultipartFile mediaFile) {
        try {
            UUID uuid = UUID.randomUUID();
            String original = mediaFile.getOriginalFilename();
            String ext = (original != null && original.contains(".")) ? original.substring(original.lastIndexOf(".")) : "";
            String fileName = uuid.toString() + ext;

            String contentType;
            switch (ext.toLowerCase()) {
                case ".jpg":
                case ".jpeg": contentType = "image/jpeg"; break;
                case ".png": contentType = "image/png"; break;
                case ".bmp": contentType = "image/bmp"; break;
                case ".gif": contentType = "image/gif"; break;
                case ".webp": contentType = "image/webp"; break;
                default: contentType = "application/octet-stream";
            }

            BlobId blobId = BlobId.of("olympick", fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .setContentDisposition("inline; filename=" + (original != null ? original : fileName))
                    .build();
            storage.create(blobInfo, mediaFile.getBytes());
            return googleCouldHeader + fileName;
        } catch (IOException e) {
            throw new RuntimeException("미디어 파일 업로드 중 오류가 발생했습니다.", e);
        }
    }

    // 기존 프로필 이미지 GCS 파일 삭제 (best-effort, 실패해도 흐름 막지 않음)
    private void deleteProfileImageQuietly(String profileURL) {
        try {
            if (profileURL == null || profileURL.isEmpty()) return;
            if (googleCouldHeader == null || !profileURL.startsWith(googleCouldHeader)) return;
            String fileName = profileURL.substring(googleCouldHeader.length());
            if (!fileName.isEmpty()) {
                storage.delete(BlobId.of("olympick", fileName));
            }
        } catch (Exception ex) {
            logger.warn("기존 프로필 이미지 삭제 실패(무시): {}", ex.getMessage());
        }
    }

    // 공개 프로필 카드 조회 — 매물 등록자(다른 사용자) 이름/프로필 표시용.
    //   본인 제한 없이 인증된 사용자라면 누구나 조회 가능. 이메일/휴대폰 등 민감정보는 제외.
    public Map<String, Object> getPublicProfile(String uid) {
        UserEntity userEntity = userRepository.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        Map<String, Object> card = new HashMap<>();
        card.put("uid", userEntity.getUid());
        card.put("name", userEntity.getName());
        card.put("nickname", userEntity.getNickname());
        card.put("profileURL", userEntity.getProfileURL());
        card.put("provider", userEntity.getProvider());
        // [B] edit by smsong - 매물 상세에서 공인중개사사무소 정보 표시용으로 공개 프로필에 포함
        card.put("agencyName", userEntity.getAgencyName());
        card.put("agencyPhone", userEntity.getAgencyPhone());
        card.put("agencyAddress", userEntity.getAgencyAddress());
        // [E] edit by smsong
        return card;
    }

    // 회원 탈퇴 (삭제)
    public UserDTO deleteUser(Long id, String uid, UserDTO userDTO, UserDetails userDetails) {
        if (!userDetails.getUsername().equals(uid)) {
            throw new RuntimeException("권한이 없습니다");
        }
        UserEntity userEntity = userRepository.findById(id).orElseThrow();
        userRepository.delete(userEntity);
        logger.info(id + "번 사용자 탈퇴 완료!");
        return UserDTO.entityToDto(userEntity);
    }

    // 카카오 로그인 URL 값 확인
    @PostConstruct
    public void logKakaoOAuthSettings() {
        logger.info("카카오 로그인 설정 값 - clientId : {}, clientSecret : {}, redirectUri : {}",
                kakaoOAuthProperties.getClientId(),
                kakaoOAuthProperties.getClientSecret(),
                kakaoOAuthProperties.getRedirectUri());

        String authorizationUrl = String.format(
                "https://kauth.kakao.com/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code",
                kakaoOAuthProperties.getClientId(),
                kakaoOAuthProperties.getRedirectUri());
        logger.info("카카오 로그인 URL : {}", authorizationUrl);
    }

    // 네이버 로그인 URL 값 확인
    @PostConstruct
    public void logNaverOAuthSettings() {
        logger.info("네이버 로그인 설정 값 - clientId : {}, clientSecret : {}, redirectUri : {}",
                naverOAuthProperties.getClientId(),
                naverOAuthProperties.getClientSecret(),
                naverOAuthProperties.getRedirectUri());

        String authorizationUrl = String.format(
                "https://nid.naver.com/oauth2.0/authorize?client_id=%s&redirect_uri=%s&response_type=code",
                naverOAuthProperties.getClientId(),
                naverOAuthProperties.getRedirectUri());
        logger.info("네이버 로그인 URL : {}", authorizationUrl);
    }

    // 구글 로그인 URL 값 확인
    @PostConstruct
    public void logGoogleOAuthSettings() {
        logger.info("구글 로그인 설정 값 - clientId : {}, clientSecret : {}, redirectUri : {}",
                googleOAuthProperties.getClientId(),
                googleOAuthProperties.getClientSecret(),
                googleOAuthProperties.getRedirectUri());

        String authorizationUrl = String.format(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=email%%20profile",
                googleOAuthProperties.getClientId(),
                googleOAuthProperties.getRedirectUri());
        logger.info("구글 로그인 URL : {}", authorizationUrl);
    }

    // 카카오 인가 코드로 액세스 토큰 요청
    public String getKakaoAccessToken(String code) {
        String url = "https://kauth.kakao.com/oauth/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoOAuthProperties.getClientId());
        params.add("redirect_uri", kakaoOAuthProperties.getRedirectUri());
        params.add("code", code);
        params.add("client_secret", kakaoOAuthProperties.getClientSecret());

        logger.info("액세스 토큰 요청 URL : {}", url);
        logger.info("액세스 토큰 요청 헤더 : {}", headers);
        logger.info("액세스 토큰 요청 파라미터 : {}", params);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                String accessToken = (String) responseBody.get("access_token");
                logger.info("액세스 토큰을 성공적으로 가져왔습니다 : {}", accessToken);
                return accessToken;
            } else {
                logger.error("액세스 토큰을 가져오는데 실패했습니다. 응답 본문이 비어있습니다.");
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("액세스 토큰을 가져오는 중 오류가 발생하였습니다. (위치: getAccessToken ) : {}", e.getMessage());
            logger.error("응답 본문 (위치: getAccessToken) : {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    // 액세스 토큰으로 사용자 정보 요청
    public Map<String, Object> getKakaoUserInfo(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                logger.info("사용자 정보를 성공적으로 가져왔습니다 : {}", responseBody);
                return responseBody;
            } else {
                logger.error("사용자 정보를 가져오는데 실패했습니다. 응답 본문이 비어있습니다.");
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("사용자 정보를 가져오는 중 오류가 발생했습니다. (위치: getUserInfo) : {}", e.getMessage());
            logger.error("응답 본문 (위치 : getUserInfo) : {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    // 카카오 로그인 처리
    public JWTDTO loginWithKakaoOAuth2(String code) {
        try {
            String accessToken = getKakaoAccessToken(code);
            Map<String, Object> userInfo = getKakaoUserInfo(accessToken);

            String uid = String.valueOf(userInfo.get("id"));
            if (uid == null) {
                throw new RuntimeException("사용자 ID를 가져올 수 없습니다.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) userInfo.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> kakaoAccount = (Map<String, Object>) userInfo.get("kakao_account");

            String name = null;
            if (properties != null) {
                name = (String) properties.get("nickname");
            }
            if (name == null) {
                name = "카카오사용자";
            }

            String email = null;
            if (kakaoAccount != null) {
                email = (String) kakaoAccount.get("email");
            }
            if (email == null) {
                throw new RuntimeException("사용자 이메일을 가져올 수 없습니다.");
            }

            // [B] edit by smsong  프로필 사진(profile_image) 추출
            // 카카오 응답 구조: kakao_account.profile.profile_image_url / thumbnail_image_url
            // (구버전 호환) properties.profile_image / thumbnail_image
            String profileImageUrl = null;
            if (kakaoAccount != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                if (profile != null) {
                    profileImageUrl = (String) profile.get("profile_image_url");
                    if (profileImageUrl == null) {
                        profileImageUrl = (String) profile.get("thumbnail_image_url");
                    }
                }
            }
            if (profileImageUrl == null && properties != null) {
                profileImageUrl = (String) properties.get("profile_image");
                if (profileImageUrl == null) {
                    profileImageUrl = (String) properties.get("thumbnail_image");
                }
            }
            logger.info("카카오 프로필 사진 URL : {}", profileImageUrl);
            // [E] edit by smsong

            UserEntity userEntity = userRepository.findByUid(uid).orElse(null);

            boolean isNewUser = false;
            if (userEntity == null) {
                userEntity = UserEntity.builder()
                        .uid(uid)
                        .name(name)
                        .email(email)
                        .profileURL(profileImageUrl) // [B][E] edit by smsong  신규 가입 시 카카오 프로필 사진 저장
                        .password(passwordEncoder.encode("oauth2user"))
                        .provider("kakao")
                        .build();
                userRepository.save(userEntity);
                isNewUser = true;
            } else {
                userEntity.setName(name);
                userEntity.setEmail(email);
                // [B] edit by smsong  프로필 사진 갱신
                // 사용자가 앱에서 직접 올린 이미지(GCS)를 매 로그인마다 덮어쓰지 않도록,
                // 기존 profileURL 이 비어있을 때만 카카오 이미지로 채운다.
                if (profileImageUrl != null
                        && (userEntity.getProfileURL() == null || userEntity.getProfileURL().isEmpty())) {
                    userEntity.setProfileURL(profileImageUrl);
                }
                // [E] edit by smsong
                userRepository.save(userEntity);
            }

            String token = jwtTokenProvider.generateToken(uid);
            logger.info("카카오 로그인 성공! 새로운 토큰이 발급되었습니다");
            return new JWTDTO(token, UserDTO.entityToDto(userEntity));
        } catch (HttpClientErrorException e) {
            logger.error("카카오 API 호출 중 오류가 발생했습니다 : {}", e.getMessage());
            logger.error("응답 본문: {}", e.getResponseBodyAsString());
            throw new RuntimeException("카카오 API 호출 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            logger.error("카카오 로그인 중 오류가 발생했습니다 (위치 : loginWithOAuth2) : {}", e.getMessage());
            throw new RuntimeException("카카오 로그인 중 오류가 발생했습니다. (위치 : loginWithOAuth2)", e);
        }
    }

    // 네이버 인가 코드로 액세스 토큰 요청
    public String getNaverAccessToken(String code) {
        String url = "https://nid.naver.com/oauth2.0/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", naverOAuthProperties.getClientId());
        params.add("client_secret", naverOAuthProperties.getClientSecret());
        params.add("redirect_uri", naverOAuthProperties.getRedirectUri());
        params.add("code", code);

        logger.info("액세스 토큰 요청 URL : {}", url);
        logger.info("액세스 토큰 요청 헤더 : {}", headers);
        logger.info("액세스 토큰 요청 파라미터 : {}", params);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                String accessToken = (String) responseBody.get("access_token");
                logger.info("액세스 토큰을 성공적으로 가져왔습니다 : {}", accessToken);
                return accessToken;
            } else {
                logger.error("액세스 토큰을 가져오는데 실패했습니다. 응답 본문이 비어있습니다.");
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("액세스 토큰을 가져오는 중 오류가 발생하였습니다. (위치: getNaverAccessToken) : {}", e.getMessage());
            logger.error("응답 본문 (위치: getNaverAccessToken) : {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    // 액세스 토큰으로 사용자 정보 요청
    public Map<String, Object> getNaverUserInfo(String accessToken) {
        String url = "https://openapi.naver.com/v1/nid/me";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        logger.info("사용자 정보 요청 URL : {}", url);
        logger.info("사용자 정보 요청 헤더 : {}", headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                logger.info("사용자 정보를 성공적으로 가져왔습니다 : {}", responseBody);
                return responseBody;
            } else {
                logger.error("사용자 정보를 가져오는데 실패했습니다. 응답 본문이 비어있습니다.");
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("사용자 정보를 가져오는 중 오류가 발생했습니다. (위치: getNaverUserInfo) : {}", e.getMessage());
            logger.error("응답 본문 (위치: getNaverUserInfo) : {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    // 네이버 로그인 처리
    public JWTDTO loginWithNaverOAuth2(String code) {
        try {
            String accessToken = getNaverAccessToken(code);
            Map<String, Object> userInfo = getNaverUserInfo(accessToken);

            Map<String, Object> response = (Map<String, Object>) userInfo.get("response");
            String uid = (String) response.get("id");
            String name = (String) response.get("name");
            String email = (String) response.get("email");

            if (uid == null || name == null || email == null) {
                throw new RuntimeException("필수 사용자 정보를 가져올 수 없습니다.");
            }

            Optional<UserEntity> userEntityOptional = userRepository.findByUid(uid);
            UserEntity userEntity;
            if (userEntityOptional.isPresent()) {
                userEntity = userEntityOptional.get();
                userEntity.setName(name);
                userEntity.setEmail(email);
            } else {
                userEntity = UserEntity.builder()
                        .uid(uid)
                        .name(name)
                        .email(email)
                        .password(passwordEncoder.encode("OAuth2_User_Password"))
                        .provider("naver")
                        .build();
                userRepository.save(userEntity);
            }
            String token = jwtTokenProvider.generateToken(uid);
            logger.info("네이버 로그인 성공! 새로운 토큰이 발급되었습니다");
            return new JWTDTO(token, UserDTO.entityToDto(userEntity));
        } catch (HttpClientErrorException e) {
            logger.error("네이버 API 호출 중 오류가 발생했습니다 : {}", e.getMessage());
            logger.error("응답 본문 : {}", e.getResponseBodyAsString());
            throw new RuntimeException("네이버 API 호출 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            logger.error("네이버 로그인 중 오류가 발생했습니다 (위치 : loginWithNaverOAuth2) : {}", e.getMessage());
            throw new RuntimeException("네이버 로그인 중 오류가 발생했습니다. (위치 : loginWithNaverOAuth2)", e);
        }
    }

    // 구글 인가 코드로 액세스 토큰 요청
    public String getGoogleAccessToken(String code) {
        String url = "https://oauth2.googleapis.com/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", googleOAuthProperties.getClientId());
        params.add("client_secret", googleOAuthProperties.getClientSecret());
        params.add("redirect_uri", googleOAuthProperties.getRedirectUri());
        params.add("code", code);

        logger.info("액세스 토큰 요청 URL : {}", url);
        logger.info("액세스 토큰 요청 헤더 : {}", headers);
        logger.info("액세스 토큰 요청 파라미터 : {}", params);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                String accessToken = (String) responseBody.get("access_token");
                logger.info("액세스 토큰을 성공적으로 가져왔습니다 : {}", accessToken);
                return accessToken;
            } else {
                logger.error("액세스 토큰을 가져오는데 실패했습니다. 응답 본문이 비어있습니다.");
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("액세스 토큰을 가져오는 중 오류가 발생하였습니다. (위치: getGoogleAccessToken) : {}", e.getMessage());
            logger.error("응답 본문 (위치: getGoogleAccessToken) : {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    // 액세스 토큰으로 사용자 정보 요청
    public Map<String, Object> getGoogleUserInfo(String accessToken) {
        String url = "https://www.googleapis.com/oauth2/v3/userinfo";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        logger.info("사용자 정보 요청 URL : {}", url);
        logger.info("사용자 정보 요청 헤더 : {}", headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                logger.info("사용자 정보를 성공적으로 가져왔습니다 : {}", responseBody);
                return responseBody;
            } else {
                logger.error("사용자 정보를 가져오는데 실패했습니다. 응답 본문이 비어있습니다.");
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("사용자 정보를 가져오는 중 오류가 발생했습니다. (위치: getGoogleUserInfo) : {}", e.getMessage());
            logger.error("응답 본문 (위치: getGoogleUserInfo) : {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    // 구글 로그인 처리
    public JWTDTO loginWithGoogleOAuth2(String code) {
        try {
            String accessToken = getGoogleAccessToken(code);
            Map<String, Object> userInfo = getGoogleUserInfo(accessToken);

            String uid = (String) userInfo.get("sub");
            String name = (String) userInfo.get("name");
            String email = (String) userInfo.get("email");

            if (uid == null || name == null || email == null) {
                throw new RuntimeException("필수 사용자 정보를 가져올 수 없습니다.");
            }

            Optional<UserEntity> userEntityOptional = userRepository.findByUid(uid);
            UserEntity userEntity;
            if (userEntityOptional.isPresent()) {
                userEntity = userEntityOptional.get();
                userEntity.setName(name);
                userEntity.setEmail(email);
            } else {
                userEntity = UserEntity.builder()
                        .uid(uid)
                        .name(name)
                        .email(email)
                        .password(passwordEncoder.encode("OAuth2_User_Password"))
                        .provider("google")
                        .build();
                userRepository.save(userEntity);
            }

            String token = jwtTokenProvider.generateToken(uid);
            logger.info("구글 로그인 성공! 새로운 토큰이 발급되었습니다");
            return new JWTDTO(token, UserDTO.entityToDto(userEntity));
        } catch (HttpClientErrorException e) {
            logger.error("구글 API 호출 중 오류가 발생했습니다 : {}", e.getMessage());
            logger.error("응답 본문: {}", e.getResponseBodyAsString());
            throw new RuntimeException("구글 API 호출 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            logger.error("구글 로그인 중 오류가 발생했습니다 (위치 : loginWithGoogleOAuth2) : {}", e.getMessage());
            throw new RuntimeException("구글 로그인 중 오류가 발생했습니다. (위치 : loginWithGoogleOAuth2)", e);
        }
    }
}
