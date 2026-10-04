// 금영 노래방 번호 찾기 (Vercel 무료 서버 함수)
// 앱이 /api/karaoke?type=song&q=제목 으로 부르면, 무료 공개 노래방 검색 서비스에서
// 금영(kumyoung) 번호를 받아 그대로 돌려줘요. 브라우저가 직접 못 가져오는 경우를 위한 중계역할이에요.
// 돈이나 열쇠(API 키)는 필요 없어요.

const TYPES = ["song", "singer", "no"];

module.exports = async (req, res) => {
  const q = String((req.query && req.query.q) || "").trim().slice(0, 60);
  const type = TYPES.includes(req.query && req.query.type) ? req.query.type : "song";
  if (!q) return res.status(400).json({ error: "검색어가 없어요" });

  try {
    const url = `https://api.manana.kr/karaoke/${type}/${encodeURIComponent(q)}/kumyoung.json`;
    const r = await fetch(url, { headers: { "User-Agent": "sixman-vietnam-guide" } });
    if (!r.ok) throw new Error("upstream " + r.status);
    const data = await r.json();
    const list = Array.isArray(data)
      ? data.map(s => ({ no: s.no, title: s.title, singer: s.singer })).slice(0, 100)
      : [];
    res.setHeader("Cache-Control", "public, s-maxage=86400, stale-while-revalidate=604800");
    return res.status(200).json(list);
  } catch (e) {
    return res.status(502).json({ error: "노래방 번호를 불러오지 못했어요" });
  }
};
