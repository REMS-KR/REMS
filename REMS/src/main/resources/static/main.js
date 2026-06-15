// =====================================================
// DATA STORE
// =====================================================
const DB_KEY = 'mybuilding_v1';

function loadData() {
    try {
        return JSON.parse(localStorage.getItem(DB_KEY)) || { buildings: [] };
    } catch { return { buildings: [] }; }
}

function saveData(data) {
    localStorage.setItem(DB_KEY, JSON.stringify(data));
}

let state = loadData();

// Demo data if empty
if (state.buildings.length === 0) {
    state.buildings = [
        {
            id: 'b1', name: '강남 상가빌딩', address: '서울 강남구 역삼동 823-5', type: 'commercial',
            lat: 37.4979, lng: 127.0276, floors: 5, totalUnits: 8,
            memo: '1층 편의점, 2-3층 사무실, 4-5층 주거',
            units: [
                { id: 'u1', floor: 1, name: '101호', type: 'commercial', status: 'occupied', area: 82, tenant: '세븐일레븐', deposit: 5000, rent: 250, manage: 30, contractStart: '2023-01-01', contractEnd: '2025-12-31', memo: '장기계약' },
                { id: 'u2', floor: 2, name: '201호', type: 'office', status: 'occupied', area: 45, tenant: '(주)테크스타트', deposit: 2000, rent: 120, manage: 15, contractStart: '2024-03-01', contractEnd: '2025-02-28', memo: '' },
                { id: 'u3', floor: 2, name: '202호', type: 'office', status: 'empty', area: 40, tenant: '', deposit: 0, rent: 0, manage: 0, contractStart: '', contractEnd: '', memo: '인테리어 완료' },
                { id: 'u4', floor: 3, name: '301호', type: 'office', status: 'expiring', area: 50, tenant: '김철수 세무사', deposit: 2500, rent: 130, manage: 15, contractStart: '2023-04-01', contractEnd: '2025-03-31', memo: '재계약 협의중' },
                { id: 'u5', floor: 4, name: '401호', type: 'residential', status: 'occupied', area: 65, tenant: '이민수', deposit: 30000, rent: 0, manage: 10, contractStart: '2024-06-01', contractEnd: '2026-05-31', memo: '전세' },
                { id: 'u6', floor: 5, name: '501호', type: 'residential', status: 'empty', area: 60, tenant: '', deposit: 0, rent: 0, manage: 0, contractStart: '', contractEnd: '', memo: '수리 필요' },
            ]
        },
        {
            id: 'b2', name: '마포 주거용 빌라', address: '서울 마포구 서교동 407-12', type: 'residential',
            lat: 37.5537, lng: 126.9137, floors: 4, totalUnits: 6,
            memo: '전층 주거용',
            units: [
                { id: 'u7', floor: 1, name: '101호', type: 'residential', status: 'occupied', area: 33, tenant: '박지원', deposit: 15000, rent: 0, manage: 5, contractStart: '2023-07-01', contractEnd: '2025-06-30', memo: '' },
                { id: 'u8', floor: 1, name: '102호', type: 'residential', status: 'expiring', area: 33, tenant: '최수진', deposit: 8000, rent: 60, manage: 5, contractStart: '2024-01-01', contractEnd: '2025-12-31', memo: '만기시 퇴거 예정' },
                { id: 'u9', floor: 2, name: '201호', type: 'residential', status: 'occupied', area: 40, tenant: '정호준', deposit: 20000, rent: 0, manage: 5, contractStart: '2024-09-01', contractEnd: '2026-08-31', memo: '' },
                { id: 'u10', floor: 3, name: '301호', type: 'residential', status: 'occupied', area: 40, tenant: '한미영', deposit: 9000, rent: 65, manage: 5, contractStart: '2023-11-01', contractEnd: '2025-10-31', memo: '' },
                { id: 'u11', floor: 4, name: '401호', type: 'residential', status: 'empty', area: 45, tenant: '', deposit: 0, rent: 0, manage: 0, contractStart: '', contractEnd: '', memo: '대수선 완료' },
                { id: 'u12', floor: 4, name: '402호', type: 'residential', status: 'occupied', area: 45, tenant: '송민지', deposit: 10000, rent: 70, manage: 5, contractStart: '2024-04-01', contractEnd: '2026-03-31', memo: '' },
            ]
        }
    ];
    saveData(state);
}

// =====================================================
// MAP
// =====================================================
let map, geocoder;
let markers = [];
let overlays = [];
let pickerMode = false;
let pickerLatlng = null;
let currentBuilding = null;
let activeFilter = 'all';
let activeTab = 'map';

const STATUS_COLOR = { empty: '#dc2626', occupied: '#0d9451', expiring: '#d97706' };
const STATUS_LABEL = { empty: '공실', occupied: '임차', expiring: '만기임박' };
const TYPE_EMOJI = { commercial: '🏪', residential: '🏠', office: '🏢', mixed: '🏗️' };

function initMap() {
    const container = document.getElementById('map');

    // Fallback center: Seoul
    const center = new kakao.maps.LatLng(37.5665, 126.9780);

    map = new kakao.maps.Map(container, {
        center: center,
        level: 7
    });

    geocoder = new kakao.maps.services.Geocoder();

    kakao.maps.event.addListener(map, 'click', function(e) {
        if (pickerMode) {
            pickerLatlng = e.latLng;
        }
    });

    renderMarkers();
    updateStats();
    showBuildingList();
    showSheet('');

    // Zoom controls
    document.getElementById('zoom-in-btn').onclick = () => map.setLevel(map.getLevel() - 1);
    document.getElementById('zoom-out-btn').onclick = () => map.setLevel(map.getLevel() + 1);
    document.getElementById('my-location-btn').onclick = gotoMyLocation;
    document.getElementById('add-btn-float').onclick = startAddBuilding;
    document.getElementById('map-picker-confirm').onclick = confirmPickerLocation;
}

function gotoMyLocation() {
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(pos => {
            const latlng = new kakao.maps.LatLng(pos.coords.latitude, pos.coords.longitude);
            map.setCenter(latlng);
            map.setLevel(4);
        });
    } else {
        showToast('위치 권한이 필요합니다');
    }
}

function renderMarkers() {
    overlays.forEach(o => o.setMap(null));
    overlays = [];

    state.buildings.forEach(b => {
        if (!matchesFilter(b)) return;

        const unitStats = getUnitStats(b);
        const dominant = unitStats.empty > 0 ? 'empty' : (unitStats.expiring > 0 ? 'expiring' : 'occupied');
        const color = STATUS_COLOR[dominant];

        const content = `
      <div onclick="selectBuilding('${b.id}')" style="
        position:relative; cursor:pointer;
        background:white; border:2.5px solid ${color};
        border-radius:12px; padding:5px 10px;
        box-shadow:0 3px 10px rgba(0,0,0,0.18);
        font-family:-apple-system,sans-serif;
        min-width:70px; text-align:center;
        transform:translateX(-50%) translateY(-100%);
        margin-bottom:8px;
      ">
        <div style="font-size:12px;font-weight:700;color:#111;">${b.name}</div>
        <div style="font-size:10px;color:${color};font-weight:600;margin-top:1px;">
          공실 ${unitStats.empty} · 임차 ${unitStats.occupied}
        </div>
        <div style="position:absolute;bottom:-7px;left:50%;transform:translateX(-50%);
          width:0;height:0;border-left:7px solid transparent;
          border-right:7px solid transparent;border-top:7px solid ${color};">
        </div>
      </div>
    `;

        const overlay = new kakao.maps.CustomOverlay({
            position: new kakao.maps.LatLng(b.lat, b.lng),
            content: content,
            yAnchor: 1
        });
        overlay.setMap(map);
        overlays.push(overlay);
    });
}

function matchesFilter(b) {
    if (activeFilter === 'all') return true;
    if (activeFilter === 'residential' || activeFilter === 'commercial' || activeFilter === 'office') {
        return b.type === activeFilter;
    }
    const s = getUnitStats(b);
    if (activeFilter === 'empty') return s.empty > 0;
    if (activeFilter === 'occupied') return s.occupied > 0;
    if (activeFilter === 'expiring') return s.expiring > 0;
    return true;
}

function getUnitStats(b) {
    const units = b.units || [];
    return {
        empty: units.filter(u => u.status === 'empty').length,
        occupied: units.filter(u => u.status === 'occupied').length,
        expiring: units.filter(u => u.status === 'expiring').length,
        total: units.length
    };
}

function selectBuilding(id) {
    currentBuilding = state.buildings.find(b => b.id === id);
    if (!currentBuilding) return;

    if (typeof map !== 'undefined' && map) {
        map.panTo(new kakao.maps.LatLng(currentBuilding.lat, currentBuilding.lng));
    }
    showBuildingDetail(currentBuilding);
    showSheet('center');
}

// =====================================================
// STATS
// =====================================================
function updateStats() {
    let totalEmpty = 0, totalOccupied = 0, totalExpiring = 0;
    state.buildings.forEach(b => {
        const s = getUnitStats(b);
        totalEmpty += s.empty;
        totalOccupied += s.occupied;
        totalExpiring += s.expiring;
    });
    document.getElementById('cnt-all').textContent = state.buildings.length;
    document.getElementById('cnt-empty').textContent = totalEmpty;
    document.getElementById('cnt-occupied').textContent = totalOccupied;
    document.getElementById('cnt-expiring').textContent = totalExpiring;
}

// =====================================================
// BOTTOM SHEET
// =====================================================
function showSheet(state_) {
    const sheet = document.getElementById('bottom-sheet');
    sheet.className = 'peek half full center'.includes(state_) ? state_ : '';
    if (state_) sheet.classList.add(state_);
}

function closeSheet() {
    showSheet('');
    currentBuilding = null;
}

let sheetStartY = 0;
let sheetStartClass = '';
const handle = document.getElementById('sheet-handle');
handle.addEventListener('touchstart', e => {
    sheetStartY = e.touches[0].clientY;
    sheetStartClass = document.getElementById('bottom-sheet').className.split(' ').find(c => ['peek','half','full'].includes(c)) || 'peek';
}, { passive: true });
handle.addEventListener('touchend', e => {
    const endY = e.changedTouches[0].clientY;
    const delta = sheetStartY - endY;
    if (delta > 40) {
        showSheet(sheetStartClass === 'peek' ? 'half' : 'full');
    } else if (delta < -40) {
        showSheet(sheetStartClass === 'full' ? 'half' : 'peek');
    }
}, { passive: true });

function showBuildingList() {
    document.getElementById('sheet-title').textContent = '내 건물 목록';
    document.getElementById('sheet-subtitle').textContent = `총 ${state.buildings.length}개 건물 관리중`;

    const body = document.getElementById('sheet-body');
    if (state.buildings.length === 0) {
        body.innerHTML = `<div class="empty-state">
      <div class="empty-state-icon">🏗️</div>
      <div class="empty-state-title">등록된 건물이 없습니다</div>
      <div class="empty-state-sub">+ 버튼을 눌러 첫 번째 건물을 추가해보세요</div>
    </div>`;
        return;
    }

    body.innerHTML = state.buildings.map(b => {
        const s = getUnitStats(b);
        const pct = s.total > 0 ? Math.round((s.occupied / s.total) * 100) : 0;
        return `<div class="building-list-item" onclick="selectBuilding('${b.id}')">
      <div class="building-thumb">${TYPE_EMOJI[b.type] || '🏢'}</div>
      <div style="flex:1;min-width:0;">
        <div class="building-list-name">${b.name}</div>
        <div class="building-list-addr">${b.address}</div>
        <div style="margin-top:4px;display:flex;gap:6px;">
          ${s.empty > 0 ? `<span style="font-size:11px;color:#dc2626;font-weight:600;">공실 ${s.empty}</span>` : ''}
          ${s.expiring > 0 ? `<span style="font-size:11px;color:#d97706;font-weight:600;">만기 ${s.expiring}</span>` : ''}
        </div>
      </div>
      <div class="building-occupancy">
        <div class="occupancy-pct">${pct}%</div>
        <div class="occupancy-label">점유율</div>
        <div class="occupancy-bar"><div class="occupancy-fill" style="width:${pct}%"></div></div>
      </div>
    </div>`;
    }).join('');
}

function showBuildingDetail(b) {
    const s = getUnitStats(b);
    const totalRent = b.units.filter(u => u.status === 'occupied').reduce((sum, u) => sum + (u.rent || 0), 0);
    const totalDeposit = b.units.filter(u => u.status !== 'empty').reduce((sum, u) => sum + (u.deposit || 0), 0);

    document.getElementById('sheet-title').textContent = b.name;
    document.getElementById('sheet-subtitle').textContent = b.address;

    const body = document.getElementById('sheet-body');
    body.innerHTML = `
    <div style="margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap;">
      <button onclick="openEditBuilding('${b.id}')" style="padding:7px 14px;border-radius:8px;border:1px solid #e5e7eb;background:#fff;font-size:13px;font-weight:600;color:#374151;cursor:pointer;">✏️ 건물 수정</button>
      <button onclick="openAddUnit('${b.id}')" style="padding:7px 14px;border-radius:8px;border:none;background:#1a56db;font-size:13px;font-weight:600;color:#fff;cursor:pointer;">+ 호실 추가</button>
      <button onclick="showBuildingList();showSheet('half')" style="padding:7px 14px;border-radius:8px;border:1px solid #e5e7eb;background:#fff;font-size:13px;font-weight:600;color:#6b7280;cursor:pointer;">← 목록</button>
    </div>

    <div class="building-info-grid">
      <div class="info-card">
        <div class="info-card-label">총 호실</div>
        <div class="info-card-value">${s.total}호</div>
      </div>
      <div class="info-card">
        <div class="info-card-label">공실</div>
        <div class="info-card-value" style="color:#dc2626;">${s.empty}호</div>
      </div>
      <div class="info-card">
        <div class="info-card-label">월 수입</div>
        <div class="info-card-value">${totalRent.toLocaleString()}만원</div>
      </div>
      <div class="info-card">
        <div class="info-card-label">보증금 합계</div>
        <div class="info-card-value">${totalDeposit.toLocaleString()}만원</div>
      </div>
    </div>

    ${b.memo ? `<div style="padding:10px 12px;background:#f9fafb;border-radius:10px;font-size:13px;color:#6b7280;margin-bottom:12px;">📝 ${b.memo}</div>` : ''}

    <div style="font-size:13px;font-weight:700;color:#374151;margin-bottom:8px;">호실 현황</div>
    <div class="unit-list">
      ${b.units.length === 0 ? '<div style="text-align:center;padding:20px;color:#9ca3af;font-size:14px;">등록된 호실이 없습니다</div>' :
        b.units.map(u => `
          <div class="unit-item ${u.status}" onclick="openUnitDetail('${b.id}','${u.id}')">
            <div>
              <div class="unit-status-badge">${STATUS_LABEL[u.status]}</div>
            </div>
            <div style="flex:1;min-width:0;">
              <div class="unit-name">${u.name} <span style="font-weight:400;color:#9ca3af;font-size:12px;">${u.floor}층</span></div>
              <div class="unit-detail">${u.area}㎡ · ${u.tenant || '공실'}</div>
            </div>
            <div class="unit-rent">
              ${u.status !== 'empty' ? `
                <div class="unit-rent-main">${u.rent > 0 ? u.rent.toLocaleString()+'만원' : '전세'}</div>
                <div class="unit-rent-sub">보 ${u.deposit.toLocaleString()}만</div>
              ` : '<div class="unit-rent-main" style="color:#dc2626;">공실</div>'}
            </div>
          </div>
        `).join('')
    }
    </div>
  `;
}

// =====================================================
// MODALS — Building
// =====================================================
function startAddBuilding() {
    // Enter picker mode
    pickerMode = true;
    pickerLatlng = map.getCenter();
    document.getElementById('map-picker-bar').classList.add('show');
    document.getElementById('map-picker-crosshair').classList.add('show');
    document.getElementById('map-picker-confirm').classList.add('show');
    showSheet('');
    document.getElementById('sheet-body').innerHTML = `<div class="empty-state"><div class="empty-state-icon">📍</div><div class="empty-state-title">지도를 이동하여 위치 설정</div><div class="empty-state-sub">건물 위치를 지도 위에서 직접 설정하세요</div></div>`;
}

function confirmPickerLocation() {
    const center = map.getCenter();
    pickerLatlng = center;
    pickerMode = false;
    document.getElementById('map-picker-bar').classList.remove('show');
    document.getElementById('map-picker-crosshair').classList.remove('show');
    document.getElementById('map-picker-confirm').classList.remove('show');

    // Reverse geocode
    geocoder.coord2Address(center.getLng(), center.getLat(), (result, status) => {
        let addr = '';
        if (status === kakao.maps.services.Status.OK) {
            addr = result[0].address.address_name;
        }
        openBuildingForm(null, center.getLat(), center.getLng(), addr);
    });
}

function openBuildingForm(building, lat, lng, addr) {
    const isEdit = !!building;
    document.getElementById('modal-title').textContent = isEdit ? '건물 수정' : '건물 추가';

    document.getElementById('modal-body').innerHTML = `
    <div class="form-section-title">기본 정보</div>
    <div class="form-group">
      <label class="form-label">건물명 *</label>
      <input id="f-name" class="form-input" type="text" placeholder="예: 강남 상가빌딩" value="${building ? building.name : ''}">
    </div>
    <div class="form-group">
      <label class="form-label">주소</label>
      <input id="f-addr" class="form-input" type="text" placeholder="주소" value="${building ? building.address : (addr || '')}">
    </div>
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">층수</label>
        <input id="f-floors" class="form-input" type="number" min="1" max="50" placeholder="층" value="${building ? building.floors : ''}">
      </div>
      <div class="form-group">
        <label class="form-label">건물 유형</label>
        <select id="f-type" class="form-select">
          <option value="commercial" ${building && building.type==='commercial'?'selected':''}>상가</option>
          <option value="residential" ${building && building.type==='residential'?'selected':''}>주거용</option>
          <option value="office" ${building && building.type==='office'?'selected':''}>사무용</option>
          <option value="mixed" ${building && building.type==='mixed'?'selected':''}>복합</option>
        </select>
      </div>
    </div>
    <div class="form-group">
      <label class="form-label">메모</label>
      <textarea id="f-memo" class="form-textarea" placeholder="특이사항, 관리 메모 등">${building ? building.memo : ''}</textarea>
    </div>
  `;

    document.getElementById('modal-footer').innerHTML = `
    ${isEdit ? `<button class="btn-danger" onclick="deleteBuilding('${building.id}')">삭제</button>` : ''}
    <button class="btn-secondary" onclick="closeModal()">취소</button>
    <button class="btn-primary" onclick="saveBuilding('${isEdit ? building.id : ''}', ${lat||building?.lat}, ${lng||building?.lng})">저장</button>
  `;

    showModal();
}

function openEditBuilding(id) {
    const b = state.buildings.find(b => b.id === id);
    if (b) openBuildingForm(b, b.lat, b.lng, b.address);
}

function saveBuilding(id, lat, lng) {
    const name = document.getElementById('f-name').value.trim();
    if (!name) { showToast('건물명을 입력하세요'); return; }

    const data = {
        name,
        address: document.getElementById('f-addr').value.trim(),
        floors: parseInt(document.getElementById('f-floors').value) || 1,
        type: document.getElementById('f-type').value,
        memo: document.getElementById('f-memo').value.trim(),
        lat: parseFloat(lat), lng: parseFloat(lng)
    };

    if (id) {
        const idx = state.buildings.findIndex(b => b.id === id);
        state.buildings[idx] = { ...state.buildings[idx], ...data };
    } else {
        state.buildings.push({ id: 'b' + Date.now(), units: [], ...data });
    }

    saveData(state);
    closeModal();
    renderMarkers();
    updateStats();
    showBuildingList();
    showSheet('half');
    showToast(id ? '건물 정보가 수정되었습니다' : '건물이 추가되었습니다');
}

function deleteBuilding(id) {
    if (!confirm('건물과 모든 호실 정보가 삭제됩니다. 계속하시겠습니까?')) return;
    state.buildings = state.buildings.filter(b => b.id !== id);
    saveData(state);
    closeModal();
    renderMarkers();
    updateStats();
    showBuildingList();
    showSheet('half');
    currentBuilding = null;
    showToast('건물이 삭제되었습니다');
}

// =====================================================
// MODALS — Unit
// =====================================================
function openAddUnit(buildingId) {
    const b = state.buildings.find(b => b.id === buildingId);
    openUnitForm(buildingId, null);
}

function openUnitDetail(buildingId, unitId) {
    const b = state.buildings.find(b => b.id === buildingId);
    const u = b.units.find(u => u.id === unitId);
    if (!u) return;

    document.getElementById('modal-title').textContent = `${u.name} 상세`;
    document.getElementById('modal-body').innerHTML = `
    <div class="unit-tabs">
      <div class="unit-tab active" onclick="switchUnitTab('info')">기본정보</div>
      <div class="unit-tab" onclick="switchUnitTab('contract')">계약정보</div>
      <div class="unit-tab" onclick="switchUnitTab('memo')">메모</div>
    </div>
    <div id="unit-tab-content"></div>
  `;

    window._unitTabData = { buildingId, unitId, u };
    renderUnitTab('info');

    document.getElementById('modal-footer').innerHTML = `
    <button class="btn-secondary" onclick="closeModal()">닫기</button>
    <button class="btn-primary" onclick="openUnitForm('${buildingId}','${unitId}')">수정</button>
  `;
    showModal();
}

function switchUnitTab(tab) {
    document.querySelectorAll('.unit-tab').forEach((t,i) => {
        t.classList.toggle('active', ['info','contract','memo'][i] === tab);
    });
    renderUnitTab(tab);
}

function renderUnitTab(tab) {
    const { u } = window._unitTabData;
    const c = document.getElementById('unit-tab-content');

    const statusColors = { empty: '#dc2626', occupied: '#0d9451', expiring: '#d97706' };
    const statusBg = { empty: '#fee2e2', occupied: '#d1fae5', expiring: '#fef3c7' };

    if (tab === 'info') {
        c.innerHTML = `
      <div style="display:inline-block;padding:5px 12px;border-radius:20px;background:${statusBg[u.status]};color:${statusColors[u.status]};font-size:13px;font-weight:700;margin-bottom:12px;">
        ${STATUS_LABEL[u.status]}
      </div>
      <div class="building-info-grid">
        <div class="info-card"><div class="info-card-label">호실</div><div class="info-card-value">${u.name}</div></div>
        <div class="info-card"><div class="info-card-label">층</div><div class="info-card-value">${u.floor}층</div></div>
        <div class="info-card"><div class="info-card-label">면적</div><div class="info-card-value">${u.area}㎡</div></div>
        <div class="info-card"><div class="info-card-label">유형</div><div class="info-card-value">${{commercial:'상가',residential:'주거',office:'사무실'}[u.type]||u.type}</div></div>
      </div>
      <div class="info-card" style="margin-top:8px;">
        <div class="info-card-label">임차인</div>
        <div class="info-card-value">${u.tenant || '—'}</div>
      </div>
    `;
    } else if (tab === 'contract') {
        c.innerHTML = `
      <div class="building-info-grid">
        <div class="info-card"><div class="info-card-label">보증금</div><div class="info-card-value">${u.deposit ? u.deposit.toLocaleString()+'만원' : '—'}</div></div>
        <div class="info-card"><div class="info-card-label">월세</div><div class="info-card-value">${u.rent ? u.rent.toLocaleString()+'만원' : (u.deposit ? '전세' : '—')}</div></div>
        <div class="info-card"><div class="info-card-label">관리비</div><div class="info-card-value">${u.manage ? u.manage+'만원' : '—'}</div></div>
        <div class="info-card"><div class="info-card-label">계약기간</div>
          <div class="info-card-value" style="font-size:12px;">${u.contractStart ? u.contractStart+'~'+u.contractEnd : '—'}</div>
        </div>
      </div>
      ${u.contractEnd ? (() => {
            const daysLeft = Math.ceil((new Date(u.contractEnd) - new Date()) / 86400000);
            const color = daysLeft < 90 ? '#dc2626' : daysLeft < 180 ? '#d97706' : '#0d9451';
            return `<div style="padding:10px 12px;background:#f9fafb;border-radius:10px;font-size:13px;margin-top:8px;color:${color};font-weight:600;">
          만기까지 D-${daysLeft > 0 ? daysLeft : '만기'}
        </div>`;
        })() : ''}
    `;
    } else {
        c.innerHTML = `<div style="padding:12px;background:#f9fafb;border-radius:10px;font-size:14px;color:#374151;min-height:80px;">
      ${u.memo || '<span style="color:#9ca3af;">메모가 없습니다</span>'}
    </div>`;
    }
}

function openUnitForm(buildingId, unitId) {
    const b = state.buildings.find(b => b.id === buildingId);
    const u = unitId ? b.units.find(u => u.id === unitId) : null;
    const isEdit = !!u;

    document.getElementById('modal-title').textContent = isEdit ? '호실 수정' : '호실 추가';
    document.getElementById('modal-body').innerHTML = `
    <div class="form-section-title">기본 정보</div>
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">호실명 *</label>
        <input id="uf-name" class="form-input" placeholder="예: 101호" value="${u ? u.name : ''}">
      </div>
      <div class="form-group">
        <label class="form-label">층</label>
        <input id="uf-floor" class="form-input" type="number" min="1" placeholder="층" value="${u ? u.floor : ''}">
      </div>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">면적 (㎡)</label>
        <input id="uf-area" class="form-input" type="number" placeholder="㎡" value="${u ? u.area : ''}">
      </div>
      <div class="form-group">
        <label class="form-label">유형</label>
        <select id="uf-type" class="form-select">
          <option value="commercial" ${u && u.type==='commercial'?'selected':''}>상가</option>
          <option value="residential" ${u && u.type==='residential'?'selected':''}>주거용</option>
          <option value="office" ${u && u.type==='office'?'selected':''}>사무용</option>
        </select>
      </div>
    </div>
    <div class="form-group">
      <label class="form-label">현황</label>
      <div class="status-selector">
        <div class="status-option ${!u || u.status==='empty'?'selected':''}" data-status="empty" onclick="selectStatus('empty')">공실</div>
        <div class="status-option ${u && u.status==='occupied'?'selected':''}" data-status="occupied" onclick="selectStatus('occupied')">임차중</div>
        <div class="status-option ${u && u.status==='expiring'?'selected':''}" data-status="expiring" onclick="selectStatus('expiring')">만기임박</div>
      </div>
    </div>

    <div class="form-section-title">임차인 정보</div>
    <div class="form-group">
      <label class="form-label">임차인명</label>
      <input id="uf-tenant" class="form-input" placeholder="임차인 이름" value="${u ? u.tenant : ''}">
    </div>
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">보증금 (만원)</label>
        <input id="uf-deposit" class="form-input" type="number" placeholder="0" value="${u ? u.deposit : ''}">
      </div>
      <div class="form-group">
        <label class="form-label">월세 (만원)</label>
        <input id="uf-rent" class="form-input" type="number" placeholder="0 = 전세" value="${u ? u.rent : ''}">
      </div>
    </div>
    <div class="form-group">
      <label class="form-label">관리비 (만원)</label>
      <input id="uf-manage" class="form-input" type="number" placeholder="0" value="${u ? u.manage : ''}">
    </div>
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">계약 시작</label>
        <input id="uf-start" class="form-input" type="date" value="${u ? u.contractStart : ''}">
      </div>
      <div class="form-group">
        <label class="form-label">계약 만료</label>
        <input id="uf-end" class="form-input" type="date" value="${u ? u.contractEnd : ''}">
      </div>
    </div>
    <div class="form-group">
      <label class="form-label">메모</label>
      <textarea id="uf-memo" class="form-textarea">${u ? u.memo : ''}</textarea>
    </div>
  `;

    document.getElementById('modal-footer').innerHTML = `
    ${isEdit ? `<button class="btn-danger" onclick="deleteUnit('${buildingId}','${unitId}')">삭제</button>` : ''}
    <button class="btn-secondary" onclick="closeModal()">취소</button>
    <button class="btn-primary" onclick="saveUnit('${buildingId}','${isEdit ? unitId : ''}')">저장</button>
  `;

    showModal();
}

function selectStatus(status) {
    document.querySelectorAll('.status-option').forEach(el => {
        el.classList.toggle('selected', el.dataset.status === status);
    });
}

function saveUnit(buildingId, unitId) {
    const name = document.getElementById('uf-name').value.trim();
    if (!name) { showToast('호실명을 입력하세요'); return; }

    const status = document.querySelector('.status-option.selected')?.dataset.status || 'empty';
    const data = {
        name, status,
        floor: parseInt(document.getElementById('uf-floor').value) || 1,
        area: parseFloat(document.getElementById('uf-area').value) || 0,
        type: document.getElementById('uf-type').value,
        tenant: document.getElementById('uf-tenant').value.trim(),
        deposit: parseInt(document.getElementById('uf-deposit').value) || 0,
        rent: parseInt(document.getElementById('uf-rent').value) || 0,
        manage: parseInt(document.getElementById('uf-manage').value) || 0,
        contractStart: document.getElementById('uf-start').value,
        contractEnd: document.getElementById('uf-end').value,
        memo: document.getElementById('uf-memo').value.trim()
    };

    const b = state.buildings.find(b => b.id === buildingId);
    if (unitId) {
        const idx = b.units.findIndex(u => u.id === unitId);
        b.units[idx] = { ...b.units[idx], ...data };
    } else {
        b.units.push({ id: 'u' + Date.now(), ...data });
    }

    saveData(state);
    closeModal();
    renderMarkers();
    updateStats();
    showBuildingDetail(b);
    showSheet('center');
    currentBuilding = b;
    showToast(unitId ? '호실 정보가 수정되었습니다' : '호실이 추가되었습니다');
}

function deleteUnit(buildingId, unitId) {
    if (!confirm('이 호실을 삭제하시겠습니까?')) return;
    const b = state.buildings.find(b => b.id === buildingId);
    b.units = b.units.filter(u => u.id !== unitId);
    saveData(state);
    closeModal();
    renderMarkers();
    updateStats();
    showBuildingDetail(b);
    showToast('호실이 삭제되었습니다');
}

// =====================================================
// STATS TAB
// =====================================================
function showStatsView() {
    const body = document.getElementById('sheet-body');
    let totalRent = 0, totalDeposit = 0;
    let allEmpty = 0, allOccupied = 0, allExpiring = 0;
    let totalUnits = 0;

    state.buildings.forEach(b => {
        const s = getUnitStats(b);
        allEmpty += s.empty; allOccupied += s.occupied; allExpiring += s.expiring;
        totalUnits += s.total;
        b.units.forEach(u => {
            if (u.status !== 'empty') {
                totalRent += u.rent || 0;
                totalDeposit += u.deposit || 0;
            }
        });
    });

    const pct = totalUnits > 0 ? Math.round((allOccupied / totalUnits) * 100) : 0;

    document.getElementById('sheet-title').textContent = '수익 현황';
    document.getElementById('sheet-subtitle').textContent = '전체 건물 통합 집계';

    body.innerHTML = `
    <div class="building-info-grid">
      <div class="info-card"><div class="info-card-label">월 수입 합계</div><div class="info-card-value" style="color:#1a56db;">${totalRent.toLocaleString()}만원</div></div>
      <div class="info-card"><div class="info-card-label">보증금 합계</div><div class="info-card-value">${(totalDeposit/10000).toFixed(1)}억원</div></div>
      <div class="info-card"><div class="info-card-label">점유율</div><div class="info-card-value" style="color:#0d9451;">${pct}%</div></div>
      <div class="info-card"><div class="info-card-label">공실</div><div class="info-card-value" style="color:#dc2626;">${allEmpty}호</div></div>
    </div>

    <div style="margin:8px 0;padding:14px;background:#f9fafb;border-radius:12px;">
      <div style="font-size:13px;font-weight:700;color:#374151;margin-bottom:10px;">점유율 현황</div>
      <div style="display:flex;gap:4px;height:20px;border-radius:10px;overflow:hidden;">
        <div style="flex:${allOccupied};background:#0d9451;" title="임차중"></div>
        <div style="flex:${allExpiring};background:#d97706;" title="만기임박"></div>
        <div style="flex:${allEmpty};background:#dc2626;" title="공실"></div>
      </div>
      <div style="display:flex;gap:14px;margin-top:8px;">
        <div style="font-size:12px;color:#0d9451;font-weight:600;">● 임차 ${allOccupied}호</div>
        <div style="font-size:12px;color:#d97706;font-weight:600;">● 만기임박 ${allExpiring}호</div>
        <div style="font-size:12px;color:#dc2626;font-weight:600;">● 공실 ${allEmpty}호</div>
      </div>
    </div>

    <div style="font-size:13px;font-weight:700;color:#374151;margin:12px 0 8px;">건물별 현황</div>
    ${state.buildings.map(b => {
        const s = getUnitStats(b);
        const r = b.units.filter(u=>u.status==='occupied').reduce((acc,u)=>acc+(u.rent||0),0);
        return `<div style="padding:12px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;margin-bottom:8px;cursor:pointer;" onclick="selectBuilding('${b.id}');switchTab('map')">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <div>
            <div style="font-size:14px;font-weight:700;color:#111;">${TYPE_EMOJI[b.type]} ${b.name}</div>
            <div style="font-size:12px;color:#6b7280;margin-top:2px;">${s.total}호 중 임차 ${s.occupied}호 · 공실 ${s.empty}호</div>
          </div>
          <div style="text-align:right;">
            <div style="font-size:15px;font-weight:700;color:#1a56db;">${r.toLocaleString()}만</div>
            <div style="font-size:11px;color:#9ca3af;">월 수입</div>
          </div>
        </div>
      </div>`;
    }).join('')}
  `;
}

function showSettingsView() {
    document.getElementById('sheet-title').textContent = '설정';
    document.getElementById('sheet-subtitle').textContent = '앱 설정 및 데이터 관리';

    document.getElementById('sheet-body').innerHTML = `
    <div style="display:flex;flex-direction:column;gap:10px;">
      <div style="padding:14px;background:#f9fafb;border-radius:12px;">
        <div style="font-size:13px;font-weight:700;color:#374151;margin-bottom:10px;">카카오맵 API 키 설정</div>
        <input id="kakao-key" class="form-input" type="text" placeholder="카카오 앱 키 입력" value="${localStorage.getItem('kakao_key')||''}">
        <button class="btn-primary" style="margin-top:8px;" onclick="saveKakaoKey()">저장 및 재시작</button>
        <div style="font-size:12px;color:#9ca3af;margin-top:6px;">developers.kakao.com에서 앱 등록 후 JavaScript 키를 입력하세요</div>
      </div>

      <div style="padding:14px;background:#f9fafb;border-radius:12px;">
        <div style="font-size:13px;font-weight:700;color:#374151;margin-bottom:10px;">데이터 관리</div>
        <div style="display:flex;flex-direction:column;gap:8px;">
          <button class="btn-secondary" onclick="openImportNaver()">🏢 네이버 매물 JSON 가져오기</button>
          <button class="btn-secondary" onclick="exportData()">📤 데이터 내보내기 (JSON)</button>
          <button class="btn-secondary" onclick="document.getElementById('import-file').click()">📥 데이터 가져오기</button>
          <input type="file" id="import-file" accept=".json" style="display:none" onchange="importData(event)">
          <button style="padding:13px;border-radius:12px;border:1px solid #fee2e2;background:#fff;color:#dc2626;font-size:15px;font-weight:600;cursor:pointer;" onclick="resetData()">🗑️ 전체 데이터 초기화</button>
        </div>
      </div>

      <div style="padding:14px;background:#f0f9ff;border-radius:12px;font-size:12px;color:#0369a1;line-height:1.6;">
        <strong>PWA 설치 방법</strong><br>
        Safari → 공유 버튼 → 홈 화면에 추가<br>
        Chrome → 주소창 우측 설치 아이콘 클릭
      </div>

      <div style="padding:14px;background:#f9fafb;border-radius:12px;font-size:12px;color:#6b7280;">
        마이빌딩 v1.0 · 데이터는 기기 로컬에 저장됩니다
      </div>
    </div>
  `;
}

function saveKakaoKey() {
    const key = document.getElementById('kakao-key').value.trim();
    if (key) { localStorage.setItem('kakao_key', key); location.reload(); }
}

function exportData() {
    const blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'mybuilding_backup_' + new Date().toISOString().slice(0,10) + '.json';
    a.click();
}

function importData(e) {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
        try {
            const imported = JSON.parse(ev.target.result);
            if (imported.buildings) {
                state = imported;
                saveData(state);
                location.reload();
            } else showToast('올바른 파일 형식이 아닙니다');
        } catch { showToast('파일을 읽을 수 없습니다'); }
    };
    reader.readAsText(file);
}

function resetData() {
    if (confirm('모든 데이터가 삭제됩니다. 계속하시겠습니까?')) {
        localStorage.removeItem(DB_KEY);
        location.reload();
    }
}

// =====================================================
// 네이버 부동산 매물 JSON 가져오기
// =====================================================
function openImportNaver() {
    document.getElementById('modal-title').textContent = '네이버 매물 가져오기';
    document.getElementById('modal-body').innerHTML = `
    <div style="font-size:13px;color:#6b7280;margin-bottom:10px;line-height:1.6;">
      네이버 부동산 매물의 JSON 데이터를 붙여넣으면 건물·호실로 자동 변환됩니다.
      같은 건물명이 이미 있으면 그 건물에 호실로 추가됩니다.
    </div>
    <textarea id="naver-json" class="form-textarea" style="min-height:200px;font-family:monospace;font-size:12px;" placeholder='{ "articleDetail": { ... }, "articleAddition": { ... } }'></textarea>
  `;
    document.getElementById('modal-footer').innerHTML = `
    <button class="btn-secondary" onclick="closeModal()">취소</button>
    <button class="btn-primary" onclick="importNaverJson()">변환하여 추가</button>
  `;
    showModal();
}

// 네이버 매물 JSON → 앱 건물/호실 구조로 변환
function convertNaverArticle(raw) {
    const d = raw.articleDetail || {};
    const add = raw.articleAddition || {};
    const floor = raw.articleFloor || {};
    const space = raw.articleSpace || {};
    const price = raw.articlePrice || {};
    const admin = raw.administrationCostInfo || {};

    // 건물명 / 주소 / 좌표
    const buildingName = d.articleName
        || ((d.aptName || '') + ' ' + (d.buildingName || '')).trim()
        || '이름없는 건물';
    const address = ((d.exposureAddress || add.exposureAddress || '') + ' ' + (d.detailAddress || '')).trim();
    const lat = parseFloat(d.latitude || add.latitude) || 0;
    const lng = parseFloat(d.longitude || add.longitude) || 0;

    // 건물 유형 매핑 (상가류 → commercial, 사무실 → office, 나머지 → residential)
    const typeCode = d.realestateTypeCode || add.realEstateTypeCode || '';
    let btype = 'residential';
    if (['SG', 'SMS', 'GM', 'GJCG', 'SUG'].includes(typeCode)) btype = 'commercial';
    else if (['OPST', 'OR', 'SGJT'].includes(typeCode) && /사무/.test(d.principalUse || '')) btype = 'office';

    // 층
    const floorInfo = add.floorInfo || ''; // 예: "3/25"
    const unitFloor = parseInt((floorInfo.split('/')[0] || '').replace(/[^0-9]/g, ''))
        || parseInt(floor.correspondingFloorCount) || 1;
    const totalFloors = parseInt(floor.totalFloorCount)
        || parseInt((floorInfo.split('/')[1] || '').replace(/[^0-9]/g, '')) || 1;

    // 면적 (전용 우선)
    const area = space.exclusiveSpace || parseFloat(d.area2) || space.supplySpace || parseFloat(d.area1) || 0;

    // 가격 (만원 단위)
    const deposit = price.warrantPrice || price.dealPrice || 0;
    const rent = price.rentPrice || 0;

    // 관리비 (원 → 만원)
    let manage = 0;
    if (admin.etcFeeDetails && admin.etcFeeDetails.etcFeeAmount) {
        manage = Math.round(admin.etcFeeDetails.etcFeeAmount / 10000);
    }

    // 거래유형
    const tradeType = d.tradeTypeName || add.tradeTypeName || '';

    // 호실명 (네이버 매물엔 호수가 없어 층 기준으로 생성)
    const unitName = unitFloor + '층 매물';

    // 메모 구성
    const memoParts = [];
    if (tradeType) memoParts.push(tradeType);
    if (d.roomCount) memoParts.push(`방${d.roomCount}/욕실${d.bathroomCount || 0}`);
    const dir = add.direction || d.direction;
    if (dir) memoParts.push(dir);
    if (d.articleFeatureDescription) memoParts.push(d.articleFeatureDescription.trim());
    if (d.moveInTypeName) memoParts.push(d.moveInTypeName);
    if (add.cpName) memoParts.push('출처:' + add.cpName);
    const unitMemo = memoParts.join(' | ');

    // 건물 메모 (단지 정보)
    const bmemoParts = [];
    if (d.aptHouseholdCount) bmemoParts.push(`${d.aptHouseholdCount}세대`);
    if (d.aptUseApproveYmd) bmemoParts.push(`${d.aptUseApproveYmd.slice(0, 4)}년 준공`);
    if (d.aptParkingCountPerHousehold) bmemoParts.push(`세대당주차 ${d.aptParkingCountPerHousehold}`);
    const buildingMemo = bmemoParts.join(' · ');

    const unit = {
        id: 'u' + Date.now() + Math.floor(Math.random() * 1000),
        floor: unitFloor,
        name: unitName,
        type: btype,
        status: 'occupied', // 가격 정보를 화면에 보이게 하기 위해 occupied로 (임차인은 비움)
        area: Math.round(area * 100) / 100,
        tenant: '',
        deposit, rent, manage,
        contractStart: '',
        contractEnd: '',
        memo: unitMemo
    };

    return { buildingName, address, lat, lng, btype, totalFloors, buildingMemo, unit };
}

function importNaverJson() {
    const txt = document.getElementById('naver-json').value.trim();
    if (!txt) { showToast('JSON을 붙여넣으세요'); return; }

    let raw;
    try { raw = JSON.parse(txt); } catch { showToast('JSON 형식이 올바르지 않습니다'); return; }
    if (!raw.articleDetail && !raw.articleAddition) {
        showToast('네이버 매물 데이터가 아닙니다');
        return;
    }

    const c = convertNaverArticle(raw);

    // 같은 건물명이 있으면 그 건물에 호실 추가, 없으면 새 건물 생성
    let b = state.buildings.find(x => x.name === c.buildingName);
    let isNew = false;
    if (!b) {
        isNew = true;
        b = {
            id: 'b' + Date.now(),
            name: c.buildingName, address: c.address, type: c.btype,
            lat: c.lat, lng: c.lng, floors: c.totalFloors, memo: c.buildingMemo,
            units: []
        };
        state.buildings.push(b);
    }
    b.units.push(c.unit);

    saveData(state);
    closeModal();
    renderMarkers();
    updateStats();
    showToast(isNew ? `'${c.buildingName}' 건물이 추가되었습니다` : `'${c.buildingName}'에 호실이 추가되었습니다`);

    if (typeof map !== 'undefined' && map) switchTab('map');
    selectBuilding(b.id);
}

// =====================================================
// UI HELPERS
// =====================================================
function switchTab(tab) {
    activeTab = tab;
    document.querySelectorAll('.nav-tab').forEach(t => t.classList.toggle('active', t.dataset.tab === tab));

    const mapWrap = document.getElementById('map-wrap');
    const filterBar = document.getElementById('filter-bar');

    if (tab === 'map') {
        mapWrap.style.display = '';
        filterBar.style.display = '';
        showBuildingList();
        showSheet('');
    } else {
        mapWrap.style.display = 'none';
        filterBar.style.display = 'none';
        showSheet('full');
        if (tab === 'list') showBuildingList();
        else if (tab === 'stats') showStatsView();
        else if (tab === 'settings') showSettingsView();
    }
}

function showModal() {
    document.getElementById('modal-overlay').classList.add('show');
}

function closeModal() {
    document.getElementById('modal-overlay').classList.remove('show');
}

function showToast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 2500);
}

// =====================================================
// SEARCH
// =====================================================
const searchInput = document.getElementById('search-input');
const searchResults = document.getElementById('search-results');

searchInput.addEventListener('input', e => {
    const q = e.target.value.trim().toLowerCase();
    if (!q) { searchResults.classList.remove('show'); return; }

    // Search local buildings first
    const localResults = state.buildings.filter(b =>
        b.name.toLowerCase().includes(q) || b.address.toLowerCase().includes(q)
    );

    let html = localResults.map(b => `
    <div class="search-result-item" onclick="selectBuilding('${b.id}');searchResults.classList.remove('show');searchInput.value='${b.name}'">
      <div>${b.name}</div>
      <div class="search-result-sub">${b.address}</div>
    </div>
  `).join('');

    // Also search via Kakao
    if (typeof kakao !== 'undefined' && q.length > 1) {
        const ps = new kakao.maps.services.Places();
        ps.keywordSearch(q, (data, status) => {
            if (status === kakao.maps.services.Status.OK) {
                html += data.slice(0, 3).map(p => `
          <div class="search-result-item" onclick="gotoKakaoResult('${p.y}','${p.x}');searchInput.value='${p.place_name}';searchResults.classList.remove('show')">
            <div>${p.place_name}</div>
            <div class="search-result-sub">${p.address_name}</div>
          </div>
        `).join('');
                searchResults.innerHTML = html;
                if (html) searchResults.classList.add('show');
            }
        });
    }

    searchResults.innerHTML = html;
    if (html) searchResults.classList.add('show');
    else searchResults.classList.remove('show');
});

function gotoKakaoResult(lat, lng) {
    map.panTo(new kakao.maps.LatLng(lat, lng));
    map.setLevel(4);
}

document.addEventListener('click', e => {
    if (!searchInput.contains(e.target) && !searchResults.contains(e.target)) {
        searchResults.classList.remove('show');
    }
});

// =====================================================
// FILTER
// =====================================================
document.querySelectorAll('.filter-chip').forEach(chip => {
    chip.addEventListener('click', () => {
        document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        activeFilter = chip.dataset.filter;
        renderMarkers();
    });
});

// =====================================================
// INIT
// =====================================================
// 카카오맵 키가 없거나 로드 실패 시 보여줄 안내 화면
function showMapFallback() {
    document.getElementById('map').innerHTML = `
    <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;background:#f9fafb;padding:30px;text-align:center;">
      <div style="font-size:48px;margin-bottom:16px;">🗺️</div>
      <div style="font-size:16px;font-weight:700;color:#374151;margin-bottom:8px;">카카오맵 API 키가 필요합니다</div>
      <div style="font-size:13px;color:#6b7280;margin-bottom:20px;line-height:1.6;">
        1. developers.kakao.com 접속<br>
        2. 앱 생성 → JavaScript 키 복사<br>
        3. config.js 의 KAKAO_MAP_KEY 값 교체<br>
        또는 아래 설정에서 입력
      </div>
      <button onclick="switchTab('settings')" style="padding:12px 24px;background:#1a56db;color:#fff;border:none;border-radius:12px;font-size:14px;font-weight:700;cursor:pointer;">API 키 설정하기</button>
    </div>
  `;
    showBuildingList();
    showSheet('');
    updateStats();
}

// PWA: offline support hint
window.addEventListener('online', () => showToast('인터넷에 연결되었습니다'));
window.addEventListener('offline', () => showToast('오프라인 모드 — 데이터는 로컬에 저장됩니다'));