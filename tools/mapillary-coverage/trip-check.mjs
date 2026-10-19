// Đo "chuyến đi độc lập" thật sự quanh từng biển (Node >= 18).
//   $env:MAPILLARY_TOKEN = "MLY|..."
//   node tools/mapillary-coverage/trip-check.mjs
// Với mỗi biển Mapillary trong bbox: bao nhiêu chuỗi ảnh / người chụp / ngày ĐI QUA (ảnh cách biển <= 30 m)
// và bao nhiêu thực sự THẤY biển (ảnh nằm trong danh sách ảnh của map feature).

import { readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const TOKEN = process.env.MAPILLARY_TOKEN;
if (!TOKEN) { console.error("Thiếu MAPILLARY_TOKEN"); process.exit(1); }

// Mặc định: đoạn quanh cổng Học viện CNBCVT trên đường Trần Phú (Hà Đông), khoảng 600 m x 550 m.
//   node trip-check.mjs <khu> <minLon,minLat,maxLon,maxLat>   (khu = tên file out/<khu>-signs.csv)
const AREA = process.argv[2] || "ptit";
const BBOX = process.argv[3] ? process.argv[3].split(",").map(Number) : [105.7845, 20.9785, 105.7905, 20.9835];
const PASS_M = 30;
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), "out");
const AUTH = { headers: { Authorization: `OAuth ${TOKEN}` } };

async function getJson(url, tries = 4) {
  for (let i = 0; ; i++) {
    const res = await fetch(url, { ...AUTH, signal: AbortSignal.timeout(90_000) }).catch((e) => ({ ok: false, status: 0, text: async () => e.message }));
    if (res.ok) return res.json();
    const body = await res.text();
    if (/reduce the amount of data/i.test(body)) throw Object.assign(new Error("TOO_MUCH_DATA"), { tooMuch: true });
    if (i >= tries - 1) throw new Error(`HTTP ${res.status}: ${body.slice(0, 200)}`);
    await new Promise((r) => setTimeout(r, 1000 * 2 ** i));
  }
}

async function pool(items, n, fn) {
  const out = []; let next = 0;
  await Promise.all(Array.from({ length: n }, async () => { while (next < items.length) { const i = next++; out[i] = await fn(items[i]); } }));
  return out;
}

async function imagesIn(b) {
  const url = `https://graph.mapillary.com/images?fields=id,sequence,captured_at,creator,geometry&bbox=${b.map((v) => v.toFixed(6)).join(",")}&limit=2000`;
  let data = null;
  try { ({ data = [] } = await getJson(url)); } catch (e) { if (!e.tooMuch) throw e; }
  if (data && (data.length < 2000 || b[2] - b[0] < 0.0002)) return data;
  if (!data && b[2] - b[0] < 0.0002) return [];
  const [x0, y0, x1, y1] = b, mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
  return (await Promise.all([[x0, y0, mx, my], [mx, y0, x1, my], [x0, my, mx, y1], [mx, my, x1, y1]].map(imagesIn))).flat();
}

const kx = Math.cos((((BBOX[1] + BBOX[3]) / 2) * Math.PI) / 180) * 111320, ky = 110540;
const dist = ([a, b], [c, d]) => Math.hypot((a - c) * kx, (b - d) * ky);
const day = (ms) => new Date(ms).toISOString().slice(0, 10);
const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s.length ? s[Math.floor(s.length / 2)] : 0; };

// 1. Biển trong bbox (lấy từ kết quả check-coverage.mjs).
const csv = (await readFile(join(OUT_DIR, `${AREA}-signs.csv`), "utf8")).replace(/^﻿/, "").trim().split("\n");
const cols = csv[0].split(",");
const signs = csv.slice(1).map((l) => Object.fromEntries(l.split(",").map((v, i) => [cols[i], v])))
  .map((s) => ({ ...s, pos: [+s.lon, +s.lat] }))
  .filter((s) => s.pos[0] >= BBOX[0] && s.pos[0] <= BBOX[2] && s.pos[1] >= BBOX[1] && s.pos[1] <= BBOX[3]);
console.log(`${signs.length} biển Mapillary trong đoạn khảo sát`);

// 2. Mọi ảnh trong bbox (mở rộng 40 m để bắt ảnh đi qua biển ở mép).
const pad = 0.0004, cells = [];
for (let x = BBOX[0] - pad; x < BBOX[2] + pad; x += 0.001)
  for (let y = BBOX[1] - pad; y < BBOX[3] + pad; y += 0.001) cells.push([x, y, Math.min(x + 0.001, BBOX[2] + pad), Math.min(y + 0.001, BBOX[3] + pad)]);
const byId = new Map();
for (const d of (await pool(cells, 4, imagesIn)).flat()) byId.set(d.id, d);
const images = [...byId.values()].filter((i) => i.geometry && i.captured_at);
const allCreators = new Set(images.map((i) => i.creator?.id).filter(Boolean));
console.log(`${images.length} ảnh, ${new Set(images.map((i) => i.sequence)).size} chuỗi ảnh, ${allCreators.size} người chụp khác nhau`);

// 3. Với từng biển: ai đi qua, ai thấy.
const rows = await pool(signs, 4, async (s) => {
  const { images: { data: seen = [] } = {} } = await getJson(`https://graph.mapillary.com/${s.id}?fields=images.sequence,images.captured_at,images.creator`);
  const passing = images.filter((i) => dist(i.geometry.coordinates, s.pos) <= PASS_M);
  const uniq = (xs, f) => new Set(xs.map(f).filter(Boolean)).size;
  const seenYears = [...new Set(seen.map((i) => day(i.captured_at).slice(0, 4)))].sort();
  return {
    id: s.id, type: s.object_value, lon: s.pos[0], lat: s.pos[1],
    pass_seqs: uniq(passing, (i) => i.sequence), pass_creators: uniq(passing, (i) => i.creator?.id), pass_days: uniq(passing, (i) => day(i.captured_at)),
    seen_seqs: uniq(seen, (i) => i.sequence), seen_creators: uniq(seen, (i) => i.creator?.id), seen_days: uniq(seen, (i) => day(i.captured_at)),
    seen_years: seenYears.join(" "),
  };
});

await writeFile(join(OUT_DIR, `${AREA}-trip-check.csv`), "﻿" + [Object.keys(rows[0]).join(","), ...rows.map((r) => Object.values(r).join(","))].join("\n"));

const pick = (k) => rows.map((r) => r[k]);
const share = (f) => Math.round((100 * rows.filter(f).length) / rows.length);
console.log(`\nTrung vị trên mỗi biển (${rows.length} biển):`);
console.log(`  Đi qua (ảnh <= ${PASS_M} m): ${median(pick("pass_seqs"))} chuỗi ảnh, ${median(pick("pass_creators"))} người chụp, ${median(pick("pass_days"))} ngày`);
console.log(`  Thực sự thấy biển:         ${median(pick("seen_seqs"))} chuỗi ảnh, ${median(pick("seen_creators"))} người chụp, ${median(pick("seen_days"))} ngày`);
console.log(`  Biển được >=3 người khác nhau thấy: ${share((r) => r.seen_creators >= 3)}%`);
console.log(`  Biển được >=2 người khác nhau thấy: ${share((r) => r.seen_creators >= 2)}%`);
console.log(`  Biển chỉ 1 chuỗi ảnh thấy:          ${share((r) => r.seen_seqs <= 1)}%`);
console.log(`  Biển được thấy ở >=2 năm:           ${share((r) => r.seen_years.includes(" "))}%`);
const tot = rows.reduce((a, r) => [a[0] + r.seen_seqs, a[1] + r.pass_seqs], [0, 0]);
console.log(`  Tỉ lệ chuỗi đi qua mà thấy biển:    ${Math.round((100 * tot[0]) / Math.max(1, tot[1]))}% (gợi ý mức 'lần thấy âm' giả)`);
console.log(`Chi tiết: ${join(OUT_DIR, `${AREA}-trip-check.csv`)}`);
