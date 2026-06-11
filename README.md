# 🏢 마이빌딩 — 부동산 현황 관리 플랫폼

카카오맵 기반 부동산 건물/호실 현황 관리 웹앱 (PWA)

---

## ✅ 주요 기능

| 기능 | 설명 |
|------|------|
| 🗺️ **카카오맵 연동** | 지도 위에 건물 마커 표시, 클릭 시 상세 정보 |
| 🏢 **건물 관리** | 건물 추가/수정/삭제, 유형(상가/주거/사무) 분류 |
| 🚪 **호실 관리** | 각 호실별 임차인, 보증금, 월세, 계약 기간 관리 |
| 🔴 **공실 현황** | 공실/임차중/만기임박 상태 시각화 |
| 📊 **수익 현황** | 월 수입, 보증금, 점유율 통계 |
| 📱 **PWA** | 홈 화면에 설치해 앱처럼 사용 |
| 💾 **오프라인** | 데이터 로컬 저장, 인터넷 없이도 조회 가능 |
| 📤 **백업/복원** | JSON으로 데이터 내보내기/가져오기 |

---

## 🚀 시작하기

### 1. 카카오 API 키 발급
1. [kakao developers](https://developers.kakao.com) 접속
2. **내 애플리케이션** → **애플리케이션 추가**
3. 앱 이름 입력 후 생성
4. **앱 키** → **JavaScript 키** 복사
5. **플랫폼** → **Web** → 도메인 등록 (localhost 포함)

### 2. API 키 적용
`index.html` 파일 상단에서 교체:
```html
<!-- Before -->
<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=YOUR_KAKAO_API_KEY&...">

<!-- After -->
<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=발급받은_JavaScript_키&...">
```

### 3. 로컬 서버 실행
```bash
# Python 3
python3 -m http.server 8080

# Node.js (npx)
npx serve .

# VS Code: Live Server 확장 사용
```

브라우저에서 `http://localhost:8080` 접속

---

## 📱 폰 앱으로 설치

### iOS (Safari)
1. Safari로 접속
2. 공유 버튼 (□↑) 탭
3. **홈 화면에 추가** 선택

### Android (Chrome)
1. Chrome으로 접속
2. 주소창 우측 **설치** 버튼 클릭
3. 또는 메뉴 → **홈 화면에 추가**

---

## 🌐 실제 배포 (무료)

### Vercel (권장)
```bash
npm i -g vercel
vercel
```

### Netlify
```bash
netlify deploy --dir .
```

### GitHub Pages
1. GitHub 저장소 생성
2. 코드 업로드
3. Settings → Pages → Deploy from branch

---

## 📁 파일 구조

```
mybuilding/
├── index.html      # 메인 앱 (단일 파일)
├── manifest.json   # PWA 설정
├── sw.js           # Service Worker (오프라인)
├── icons/          # 앱 아이콘
│   ├── icon-192.png
│   └── icon-512.png
└── README.md
```

---

## 🔮 향후 개선 사항

- [ ] 백엔드 API 연동 (Firebase / Supabase)
- [ ] 다중 사용자 / 팀 협업
- [ ] 계약 만기 푸시 알림
- [ ] 사진 첨부 기능
- [ ] 임대차 계약서 PDF 생성
- [ ] 수익 그래프/차트
- [ ] 카카오 로그인 연동
- [ ] 건물 사진 갤러리

---

## 💡 기술 스택

- **프론트엔드**: Vanilla JS + CSS (프레임워크 없음, 번들러 없음)
- **지도**: Kakao Maps JavaScript API
- **저장소**: localStorage (브라우저 내장)
- **PWA**: Web App Manifest + Service Worker
- **배포**: 정적 파일 (어디서든 호스팅 가능)
