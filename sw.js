// 오프라인용: 앱 화면 파일을 저장해 두고, 인터넷이 없을 때 꺼내 보여줘요.
const CACHE = "hcm-guide-v19";
const FILES = ["./", "index.html", "manifest.webmanifest", "icon.svg"];
self.addEventListener("install", e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES))); self.skipWaiting(); });
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  if (e.request.url.includes(".supabase.co/")) return; // 제보 목록은 항상 최신으로
  // 먼저 인터넷에서 받고, 실패하면 저장해 둔 것을 보여줘요 (시트 내용도 마지막 것을 기억)
  e.respondWith(
    fetch(e.request).then(res => {
      const copy = res.clone();
      caches.open(CACHE).then(c => c.put(e.request, copy)).catch(() => {});
      return res;
    }).catch(() => caches.match(e.request, { ignoreSearch: true }))
  );
});
