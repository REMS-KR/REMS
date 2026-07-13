// ============================================================
// 핵방노트 서비스워커 (sw.js)
//  · Web Push 알림 수신/클릭 처리
//  · 설치형 PWA 를 위한 최소 fetch 핸들러(네트워크 우선, 캐싱은 하지 않음)
//  · 새 버전 배포 시 즉시 활성화
// ============================================================
const SW_VERSION = 'v1.0.0';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

// 네트워크 우선 통과 (오프라인 캐싱 없음). 크로스오리진(지도/구글/백엔드)은 건드리지 않음.
self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  let url;
  try { url = new URL(req.url); } catch (e) { return; }
  if (url.origin !== self.location.origin) return;   // 크로스오리진은 브라우저 기본 처리
  event.respondWith(fetch(req).catch(() => new Response('', { status: 504 })));
});

// ===== Web Push =====
self.addEventListener('push', (event) => {
  let data = { title: '핵방노트', body: '' };
  try {
    if (event.data) data = event.data.json();
  } catch (e) {
    if (event.data) data.body = event.data.text();
  }
  const title = data.title || '핵방노트';
  const options = {
    body: data.body || '',
    icon: 'icons/icon-192.png',
    badge: 'icons/icon-192.png',
    vibrate: [80, 40, 80],
    data: { url: data.url || './' },
    tag: data.tag || undefined,
    renotify: !!data.tag
  };
  event.waitUntil((async () => {
    await self.registration.showNotification(title, options);
    // 열려 있는 앱 화면에 알림 도착을 알려 배지를 갱신하게 함
    const list = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    list.forEach(c => c.postMessage({ type: 'push-received' }));
  })());
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || './';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
      for (const c of list) {
        if ('focus' in c) return c.focus();
      }
      if (self.clients.openWindow) return self.clients.openWindow(url);
    })
  );
});
