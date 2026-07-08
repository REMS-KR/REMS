/* =====================================================================
 * ai-search.js — AI 자연어 매물 검색 (중개인 전용)  [자체 스타일 주입 버전]
 *
 * 이 버전은 필요한 CSS를 스스로 <style>로 주입합니다.
 *  → main.css 를 수정하지 않아도 버튼/모달/결과 스타일이 항상 적용됩니다.
 *    (기존 ai-search-append.css 는 더 이상 붙여넣지 않아도 됩니다)
 *
 * 동작
 *   1) 플로팅 버튼(+ / 통화 버튼과 동일한 44×44 스타일) → 모달 오픈
 *   2) 자연어 입력 → POST /ai/search/{uid}
 *   3) 서버가 선별한 결과를 기존 목록 카드로 하단 시트에 표시
 *
 * main.js 로드 이후에 불러야 함(전역 재사용):
 *   getUid, authHeaders, handleResponse, API_BASE_URL, isBroker, applyPermUI,
 *   getCurrentUser, ADMIN_UID, showModal, closeModal, showToast, showLoading,
 *   hideLoading, buildingListItemHTML, selectBuilding, Sheet, effectiveStatus,
 *   hydrateOwnerNames, icon, escapeHtml, state
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

    // =====================================================
    // 스타일 자체 주입 (main.css 수정 불필요)
    // =====================================================
    const AI_CSS = `
/* AI 검색 버튼 — 통화 버튼(#call-parse-float) 위에 같은 규격으로 쌓임 */
#ai-search-float {
    position: absolute; right: 14px; bottom: 270px; z-index: 50;
    width: 44px; height: 44px; border-radius: 12px;
    background: #6d28d9; border: none; color: #ffffff;
    box-shadow: var(--shadow-md, 0 4px 12px rgba(0,0,0,0.15), 0 2px 6px rgba(0,0,0,0.10));
    display: flex; align-items: center; justify-content: center; cursor: pointer;
    transition: opacity 0.3s ease, transform 0.25s cubic-bezier(0.34,1.56,0.64,1),
                background 0.25s, box-shadow 0.2s, bottom 0.4s cubic-bezier(0.4,0,0.2,1);
}
#ai-search-float:active { transform: scale(0.93); }
#app.chrome-hidden #ai-search-float { bottom: calc(max(26px, env(safe-area-inset-bottom)) + 108px); opacity: 1; pointer-events: auto; }
#app.picker-active #ai-search-float { opacity: 0; pointer-events: none; transform: scale(0.85); }
html.mode-web #ai-search-float { right: 18px; bottom: 132px; }

/* AI 검색 모달 내부 */
.ai-search-intro {
    font-size: 13px; color: var(--gray-600, #4b5563); line-height: 1.6;
    background: #f5f3ff; border: 1px solid #ede9fe; border-radius: var(--radius-md, 12px);
    padding: 12px 14px; margin-bottom: 16px;
}
.ai-search-intro b { color: #6d28d9; }
.ai-ex-wrap { display: flex; flex-wrap: wrap; gap: 8px; }
.ai-ex-chip {
    font-size: 12.5px; color: var(--gray-700, #374151); font-weight: 600;
    background: var(--gray-100, #f3f4f6); border: 1px solid var(--gray-200, #e5e7eb);
    border-radius: 999px; padding: 7px 12px; cursor: pointer;
    transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.ai-ex-chip:hover { background: #f5f3ff; border-color: #ddd6fe; color: #6d28d9; }
.ai-ex-chip:active { transform: scale(0.97); }

/* AI 검색 결과 배너 (하단 시트 상단) */
.ai-result-banner {
    display: flex; align-items: center; gap: 8px;
    font-size: 13px; font-weight: 700; color: #6d28d9;
    background: #f5f3ff; border: 1px solid #ede9fe; border-radius: var(--radius-md, 12px);
    padding: 10px 12px; margin: 0 0 12px;
}
.ai-result-banner-ic { display: inline-flex; flex-shrink: 0; }
`;

    function ensureStyles() {
        if (document.getElementById('ai-search-styles')) return;
        const style = document.createElement('style');
        style.id = 'ai-search-styles';
        style.textContent = AI_CSS;
        (document.head || document.documentElement).appendChild(style);
    }

    // ---- 플로팅 버튼 생성 (HTML/CSS 수정 없이 #map-wrap 에 주입) ----
    function ensureButton() {
        if (document.getElementById('ai-search-float')) return;
        const mapWrap = document.getElementById('map-wrap');
        if (!mapWrap) { console.warn('[ai-search] #map-wrap 를 찾지 못함 — 버튼 주입 보류'); return; }
        const btn = document.createElement('button');
        btn.id = 'ai-search-float';
        btn.title = 'AI 매물 검색';
        btn.setAttribute('aria-label', 'AI 매물 검색');
        // ★ CSS 주입이 혹시 막혀도 지도(#map, absolute)에 가려지지 않도록 최소 위치 안전장치(인라인)
        btn.style.position = 'absolute';
        btn.style.zIndex = '50';
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

    // 관리자 여부 — uid 를 문자열로 강제 비교(숫자로 저장돼 있어도 통과)
    function isAdmin() {
        try {
            const u = (typeof getCurrentUser === 'function') ? getCurrentUser() : null;
            const adminUid = (typeof ADMIN_UID !== 'undefined') ? ADMIN_UID : '4979532269';
            return !!(u && String(u.uid) === String(adminUid));
        } catch (_) { return false; }
    }

    // 중개인/관리자에게만 노출
    function updateButtonVisibility() {
        const btn = document.getElementById('ai-search-float');
        if (!btn) return;
        const broker = (typeof isBroker === 'function') ? isBroker() : false;
        const show = isAdmin() || broker;
        btn.style.display = show ? 'flex' : 'none';
        if (!show) console.info('[ai-search] 버튼 숨김 — isAdmin=%s, isBroker=%s', isAdmin(), broker);
    }

    // main.js 의 applyPermUI 생명주기에 편승해 버튼 노출을 갱신
    if (typeof applyPermUI === 'function') {
        const _origApplyPermUI = applyPermUI;
        window.applyPermUI = function () {
            _origApplyPermUI.apply(this, arguments);
            updateButtonVisibility();
        };
    }

    // =====================================================
    // 모달
    // =====================================================
    // ICON_PATHS 에 'search'가 없어 인라인 돋보기 SVG 사용
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
        if (!(isAdmin() || (typeof isBroker === 'function' && isBroker()))) {
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
        if (_isSubmitting) return;
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
        const list = rawList.map(normalizeBuilding);
        mergeIntoState(list);   // selectBuilding 이 state.buildings 에서 찾으므로 병합 필수

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
        if (typeof Sheet !== 'undefined' && Sheet.open) Sheet.open('tab', 'full');
    }

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
        list.forEach(b => byId.set(String(b.id), b));
        state.buildings = Array.from(byId.values());
    }

    // =====================================================
    // 초기화
    // =====================================================
    function init() {
        ensureStyles();
        ensureButton();
        updateButtonVisibility();
        // #map-wrap 준비 지연 + 권한 로드 지연에 대비해 버튼이 노출될 때까지 재시도
        let tries = 0;
        const timer = setInterval(function () {
            tries++;
            ensureStyles();
            ensureButton();
            updateButtonVisibility();
            const btn = document.getElementById('ai-search-float');
            if ((btn && btn.style.display !== 'none') || tries >= 16) clearInterval(timer);
        }, 500);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();