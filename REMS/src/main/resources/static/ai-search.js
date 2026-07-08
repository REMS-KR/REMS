/* =====================================================================
 * ai-search.js — AI 자연어 매물 검색 (중개인 전용)
 *
 * 동작
 *   1) 플로팅 버튼(+ / 통화 버튼과 동일한 44×44 스타일) → 모달 오픈
 *   2) 자연어 입력 → POST /ai/search/{uid}
 *   3) 서버가 (제미나이로 조건 추출 + 코드로 정확히 선별)한 결과를
 *      기존 목록 카드(buildingListItemHTML)로 하단 시트에 표시
 *
 * main.js 로드 이후에 불러야 함(전역 함수 재사용):
 *   getUid, authHeaders, handleResponse, API_BASE_URL, isBroker, applyPermUI,
 *   showModal, closeModal, showToast, showLoading, hideLoading,
 *   buildingListItemHTML, selectBuilding, Sheet, effectiveStatus,
 *   hydrateOwnerNames, icon, state
 * ===================================================================== */
(function () {
    'use strict';

    // ---- API 호출 추가 (기존 Api 객체에 메서드 하나 붙임) ----
    if (typeof Api !== 'undefined') {
        Api.aiSearch = (query) =>
            fetch(`${API_BASE_URL}/ai/search/${getUid()}`, {
                method: 'POST',
                headers: authHeaders(true),
                body: JSON.stringify({ query })
            }).then(handleResponse);
    }

    // ---- 플로팅 버튼 생성 (HTML 수정 없이 #map-wrap 에 주입) ----
    function ensureButton() {
        if (document.getElementById('ai-search-float')) return;
        const mapWrap = document.getElementById('map-wrap');
        if (!mapWrap) return;
        const btn = document.createElement('button');
        btn.id = 'ai-search-float';
        btn.title = 'AI 매물 검색';
        btn.setAttribute('aria-label', 'AI 매물 검색');
        // 스파클(AI) 아이콘 — 라인 스타일로 기존 아이콘과 통일
        btn.innerHTML =
            '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
            'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
            '<path d="M12 3l1.9 4.6L18.5 9.5 13.9 11.4 12 16l-1.9-4.6L5.5 9.5l4.6-1.9L12 3z"/>' +
            '<path d="M19 14l.8 2 .2.8 2 .8-2 .8-.2.8-.8 2-.8-2-.2-.8-2-.8 2-.8.2-.8.8-2z"/>' +
            '</svg>';
        btn.onclick = openAiSearchModal;
        mapWrap.appendChild(btn);
        updateButtonVisibility();
    }

    // 중개인/관리자에게만 노출 (권한 미로딩 시엔 isBroker 의 임시 판정을 따름)
    function updateButtonVisibility() {
        const btn = document.getElementById('ai-search-float');
        if (!btn) return;
        const show = (typeof isBroker === 'function') ? isBroker() : false;
        btn.style.display = show ? '' : 'none';
    }

    // main.js 의 applyPermUI 생명주기에 편승해 버튼 노출을 갱신
    if (typeof applyPermUI === 'function') {
        const _origApplyPermUI = applyPermUI;
        // 전역 재정의 — 클래식 스크립트에서 bare 호출도 이 래퍼를 참조
        window.applyPermUI = function () {
            _origApplyPermUI.apply(this, arguments);
            updateButtonVisibility();
        };
    }

    // =====================================================
    // 모달
    // =====================================================
    // ICON_PATHS 에 'search'가 없어 인라인 돋보기 SVG 사용 (상단바 검색 아이콘과 동일 형태)
    function searchSvg(size) {
        const s = size || 15;
        return `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" `
            + `stroke-width="2" stroke-linecap="round" stroke-linejoin="round" `
            + `style="vertical-align:middle;flex-shrink:0;" aria-hidden="true">`
            + `<circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>`;
    }

    const EXAMPLES = [
        '강남구 보증금 5천 이하 주차되는 월세',
        '전세 3억 이하 오피스텔, 전세대출 가능',
        '공실 있는 상가, 관리비 10만원 이하',
        '역삼동 매매 20억 이하 근린생활시설'
    ];

    window.openAiSearchModal = function openAiSearchModal() {
        if (typeof isBroker === 'function' && !isBroker()) {
            showToast('AI 매물 검색은 중개인만 사용할 수 있습니다.');
            return;
        }

        document.getElementById('modal-title').textContent = 'AI 매물 검색';

        const chips = EXAMPLES.map(ex =>
            `<span class="ai-ex-chip" onclick="aiFillExample(this)">${escapeHtml(ex)}</span>`
        ).join('');

        document.getElementById('modal-body').innerHTML = `
      <div class="ai-search-intro">
        금액·위치·조건을 문장으로 적으면 AI가 조건을 파악해 <b>딱 맞는 매물</b>만 골라줍니다.
      </div>
      <div class="form-group">
        <label class="form-label">무엇을 찾으세요?</label>
        <textarea id="ai-q" class="form-textarea" rows="3"
          placeholder="예) 강남구에서 보증금 5천 이하, 주차 되는 월세 찾아줘"
          onkeydown="if((event.ctrlKey||event.metaKey)&&event.key==='Enter'){runAiSearch();}"></textarea>
      </div>
      <div class="form-group" style="margin-bottom:4px;">
        <label class="form-label" style="margin-bottom:8px;">예시 (눌러서 넣기)</label>
        <div class="ai-ex-wrap">${chips}</div>
      </div>
    `;

        document.getElementById('modal-footer').innerHTML = `
      <button class="btn-secondary" onclick="closeModal()">취소</button>
      <button class="btn-primary" onclick="runAiSearch()">${searchSvg(15)} 검색</button>
    `;

        showModal();
        setTimeout(() => { const t = document.getElementById('ai-q'); if (t) t.focus(); }, 50);
    };

    window.aiFillExample = function aiFillExample(el) {
        const t = document.getElementById('ai-q');
        if (t) { t.value = el.textContent; t.focus(); }
    };

    // =====================================================
    // 검색 실행
    // =====================================================
    window.runAiSearch = async function runAiSearch() {
        if (_isSubmitting) return;                       // 중복 호출 차단 (기존 가드 재사용)
        const t = document.getElementById('ai-q');
        const query = (t && t.value || '').trim();
        if (!query) { showToast('검색어를 입력하세요.'); return; }

        showLoading('AI가 조건을 분석 중…');
        try {
            const resp = await Api.aiSearch(query);
            closeModal();
            renderAiResults(resp, query);
        } catch (e) {
            showToast('AI 검색 실패: ' + (e.message || '알 수 없는 오류'));
        } finally {
            hideLoading();
        }
    };

    // =====================================================
    // 결과 렌더 — 기존 목록 카드/상세 흐름 재사용
    // =====================================================
    function renderAiResults(resp, query) {
        const rawList = (resp && resp.buildings) || [];

        // 목록 카드/상세가 기대하는 형태로 정규화 + state 에 병합
        //  (selectBuilding 이 state.buildings 에서 id로 찾으므로 반드시 병합)
        const list = rawList.map(normalizeBuilding);
        mergeIntoState(list);

        const title = document.getElementById('sheet-title');
        const subtitle = document.getElementById('sheet-subtitle');
        const body = document.getElementById('sheet-body');
        if (title) title.textContent = 'AI 검색 결과';
        if (subtitle) subtitle.textContent = `"${query}" · ${list.length}건`;

        const banner = `
      <div class="ai-result-banner">
        <span class="ai-result-banner-ic">${searchSvg(14)}</span>
        <span>${escapeHtml((resp && resp.summary) || '조건 해석 결과')}</span>
      </div>`;

        if (list.length === 0) {
            body.innerHTML = banner + `
        <div class="empty-state">
          <div class="empty-state-icon">${icon('building', 56, 'color:#9ca3af;')}</div>
          <div class="empty-state-title">조건에 맞는 매물이 없습니다</div>
          <div class="empty-state-sub">조건을 넓혀서 다시 검색해보세요</div>
        </div>`;
        } else {
            body.innerHTML = banner + list.map(buildingListItemHTML).join('');
            if (typeof hydrateOwnerNames === 'function') hydrateOwnerNames(body);
        }

        if (body) body.scrollTop = 0;

        // 하단 시트를 전체로 올림 (탭 전환과 동일한 방식)
        if (typeof Sheet !== 'undefined' && Sheet.open) Sheet.open('tab', 'full');
    }

    // main.js loadData 와 동일한 정규화 (id 문자열화 + 표시상 공실 반영)
    function normalizeBuilding(b) {
        const nb = Object.assign({}, b);
        nb.id = String(nb.id);
        nb.units = (nb.units || []).map(u => {
            const nu = Object.assign({}, u, { id: String(u.id) });
            nu.status = (typeof effectiveStatus === 'function') ? effectiveStatus(nu) : nu.status;
            return nu;
        });
        return nb;
    }

    function mergeIntoState(list) {
        if (typeof state === 'undefined') return;
        if (!Array.isArray(state.buildings)) state.buildings = [];
        const byId = new Map(state.buildings.map(b => [String(b.id), b]));
        list.forEach(b => byId.set(String(b.id), b));   // 있으면 갱신, 없으면 추가
        state.buildings = Array.from(byId.values());
    }

    // =====================================================
    // 초기화
    // =====================================================
    function init() {
        ensureButton();
        updateButtonVisibility();
        // 권한 로드가 조금 늦게 끝나는 경우 대비해 몇 번 더 재확인
        setTimeout(updateButtonVisibility, 800);
        setTimeout(updateButtonVisibility, 2000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
