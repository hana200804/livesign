// PoC tuần 1: chọn 500 m Trần Phú có nhiều biển nhất, lấy ảnh Mapillary đi qua từng biển (Node >= 18).
//   node tools/poc/fetch-signs.mjs            -> chỉ chọn đoạn + biển (không cần token)
//   $env:MAPILLARY_TOKEN = "MLY|..."; node tools/poc/fetch-signs.mjs
//                                             -> thêm ảnh + metadata + tải thumbnail 1024 px
// Một ảnh "nhìn được biển" khi camera cách biển 5–35 m và biển nằm trong ±40° quanh hướng camera
// (ảnh 360° luôn nhìn được). "Mapillary thấy" = ảnh nằm trong danh sách ảnh của map feature đó.

import { mkdir, readFile, writeFile, access } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(HERE, "out");
const SIGNS_CSV = join(HERE, "..", "mapillary-coverage", "out", "ptit-signs.csv");
// Token lấy từ biến môi trường, hoặc từ file tools/poc/.token (một dòng MLY|...).
const TOKEN = process.env.MAPILLARY_TOKEN || (await readFile(join(HERE, ".token"), "utf8").catch(() => "")).trim();

const BBOX = [105.782, 20.976, 105.793, 20.986]; // vùng PTIT đã khảo sát
const WINDOW_M = 500, NEAR_ROAD_M = 20, MAX_SIGNS = 20;
// Biển phải nằm trong 85% nửa khung ngang (tính từ tiêu cự Mapillary, chuẩn hóa theo cạnh dài).
const MIN_M = 5, MAX_M = 35, FRAME_KEEP = 0.85, MAX_IMG_PER_SIGN = 20;

async function getJson(url, init = {}, tries = 4) {
  for (let i = 0; ; i++) {
    const res = await fetch(url, { ...init, signal: AbortSignal.timeout(90_000) }).catch((e) => ({ ok: false, status: 0, text: async () => e.message }));
    if (res.ok) return res.json();
    const body = await res.text();
    if (i >= tries - 1) throw new Error(`HTTP ${res.status}: ${body.slice(0, 200)}`);
    await new Promise((r) => setTimeout(r, 1000 * 2 ** i));
  }
}

async function pool(items, n, fn) {
  const out = []; let next = 0;
  await Promise.all(Array.from({ length: n }, async () => { while (next < items.length) { const i = next++; out[i] = await fn(items[i], i); } }));
  return out;
}

// ---------- Hình học (phép chiếu phẳng cục bộ) ----------

const lat0 = (BBOX[1] + BBOX[3]) / 2, lon0 = (BBOX[0] + BBOX[2]) / 2;
const kx = Math.cos((lat0 * Math.PI) / 180) * 111320, ky = 110540;
const proj = ([lon, lat]) => [(lon - lon0) * kx, (lat - lat0) * ky];
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
const bearing = (from, to) => (Math.atan2(to[0] - from[0], to[1] - from[1]) * 180) / Math.PI; // 0 = bắc, theo chiều kim đồng hồ
// Góc lệch có dấu từ hướng camera tới biển: dương = biển ở bên phải khung hình.
const angDiff = (a, b) => ((((a - b) % 360) + 540) % 360) - 180;

// Chiếu điểm lên polyline: trả về [khoảng cách tới đường, vị trí dọc đường (m)].
function locate(p, line) {
  let best = [Infinity, 0], acc = 0;
  for (let i = 1; i < line.length; i++) {
    const a = line[i - 1], b = line[i], dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
    const t = len ? Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (len * len))) : 0;
    const d = dist(p, [a[0] + t * dx, a[1] + t * dy]);
    if (d < best[0]) best = [d, acc + t * len];
    acc += len;
  }
  return best;
}

// ---------- 1. Trục đường Trần Phú từ OpenStreetMap ----------

async function fetchTranPhu() {
  const [w, s, e, n] = BBOX;
  const q = `[out:json][timeout:60];way["highway"~"^trunk"]["name"~"Tr.n Ph"](${s},${w},${n},${e});out geom;`;
  const mirrors = ["https://overpass-api.de", "https://overpass.private.coffee", "https://overpass.kumi.systems"];
  let lastErr;
  for (const m of mirrors) {
    try {
      const res = await getJson(`${m}/api/interpreter`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded", "User-Agent": "doan-livesign-poc/1.0" },
        body: "data=" + encodeURIComponent(q),
      }, 2);
      return res.elements.filter((el) => el.geometry?.length >= 2);
    } catch (e) { lastErr = e; }
  }
  throw lastErr;
}

// Đường có 2 chiều tách nhau nên lấy way dài nhất làm trục, các way khác chỉ dùng để đo khoảng cách tới đường.
function centerline(ways) {
  const lines = ways.map((w) => w.geometry.map((g) => proj([g.lon, g.lat])));
  const len = (l) => l.slice(1).reduce((s, p, i) => s + dist(p, l[i]), 0);
  // Nối các way liền nhau thành chuỗi dài nhất (đầu cuối cách nhau < 3 m).
  const chains = lines.map((l) => [...l]);
  for (let merged = true; merged;) {
    merged = false;
    outer: for (let i = 0; i < chains.length; i++) for (let j = 0; j < chains.length; j++) {
      if (i === j) continue;
      const a = chains[i], b = chains[j];
      if (dist(a.at(-1), b[0]) < 3) { chains[i] = [...a, ...b.slice(1)]; chains.splice(j, 1); merged = true; break outer; }
    }
  }
  return { axis: chains.sort((a, b) => len(b) - len(a))[0], lines, axisLen: len(chains[0]) };
}

// ---------- 2. Chọn cửa sổ 500 m nhiều biển nhất ----------

function parseCsv(text) {
  const [head, ...rows] = text.replace(/^﻿/, "").trim().split(/\r?\n/);
  const cols = head.split(",");
  return rows.map((l) => Object.fromEntries(l.split(",").map((v, i) => [cols[i], v])));
}

function pickSigns(signs, axis, lines, axisLen) {
  const near = signs
    .map((s) => ({ ...s, xy: proj([+s.lon, +s.lat]) }))
    .map((s) => ({ ...s, roadDist: Math.min(...lines.map((l) => locate(s.xy, l)[0])), along: locate(s.xy, axis)[1] }))
    .filter((s) => s.roadDist <= NEAR_ROAD_M);
  let best = { start: 0, list: [] };
  for (let start = 0; start + WINDOW_M <= axisLen + 1; start += 10) {
    const list = near.filter((s) => s.along >= start && s.along <= start + WINDOW_M);
    if (list.length > best.list.length) best = { start, list };
  }
  // Ưu tiên biển được thấy lâu nhất (nhiều khả năng có ảnh ở nhiều thời điểm), mỗi loại tối đa 4 biển cho đa dạng.
  const span = (s) => new Date(s.last_seen) - new Date(s.first_seen);
  const perType = new Map(), chosen = [];
  for (const s of [...best.list].sort((a, b) => span(b) - span(a))) {
    const n = perType.get(s.object_value) ?? 0;
    if (n >= 4 || chosen.length >= MAX_SIGNS) continue;
    perType.set(s.object_value, n + 1); chosen.push(s);
  }
  return { start: best.start, inWindow: best.list.length, chosen: chosen.sort((a, b) => a.along - b.along) };
}

// ---------- 3. Ảnh đi qua từng biển (cần token) ----------

const IMG_FIELDS = "id,sequence,captured_at,creator,computed_geometry,geometry,computed_compass_angle,compass_angle,camera_type,camera_parameters,width,height,thumb_1024_url";

async function imagesAround(sign) {
  const auth = { headers: { Authorization: `OAuth ${TOKEN}` } };
  const d = 0.00035; // ~38 m
  const lon = +sign.lon, lat = +sign.lat;
  const bbox = [lon - d, lat - d, lon + d, lat + d].map((v) => v.toFixed(6)).join(",");
  const [{ data: imgs = [] }, feat] = await Promise.all([
    getJson(`https://graph.mapillary.com/images?fields=${IMG_FIELDS}&bbox=${bbox}&limit=2000`, auth),
    getJson(`https://graph.mapillary.com/${sign.id}?fields=images`, auth),
  ]);
  const sawIds = new Set((feat.images?.data ?? []).map((i) => i.id));
  const rows = [];
  for (const im of imgs) {
    const g = im.computed_geometry ?? im.geometry, heading = im.computed_compass_angle ?? im.compass_angle;
    if (!g || heading == null || !im.thumb_1024_url) continue;
    const cam = proj(g.coordinates), m = dist(cam, sign.xy);
    const pano = /spherical|equirectangular/.test(im.camera_type ?? "");
    const off = angDiff(bearing(cam, sign.xy), heading);
    const f = im.camera_parameters?.[0] || 0.8, halfW = im.width && im.height ? im.width / Math.max(im.width, im.height) / 2 : 0.5;
    const halfFov = (Math.atan(halfW / f) * 180) / Math.PI * FRAME_KEEP;
    if (m < MIN_M || m > MAX_M || (!pano && Math.abs(off) > halfFov)) continue;
    rows.push({
      sign_id: sign.id, sign_type: sign.object_value, image_id: im.id, sequence: im.sequence, creator: im.creator?.id ?? "",
      captured: new Date(im.captured_at).toISOString().slice(0, 10), dist_m: +m.toFixed(1), off_deg: +off.toFixed(1),
      pano, focal: im.camera_parameters?.[0] ?? "", width: im.width ?? "", height: im.height ?? "", mapillary_saw: sawIds.has(im.id), url: im.thumb_1024_url,
    });
  }
  // Giữ mẫu trải đều: mỗi chuỗi ảnh lấy ảnh gần biển nhất, rồi cắt còn MAX_IMG_PER_SIGN, ưu tiên nhiều năm khác nhau.
  const bySeq = new Map();
  for (const r of rows) if (!bySeq.has(r.sequence) || r.dist_m < bySeq.get(r.sequence).dist_m) bySeq.set(r.sequence, r);
  const perSeq = [...bySeq.values()].sort((a, b) => a.captured.localeCompare(b.captured));
  const step = Math.max(1, perSeq.length / MAX_IMG_PER_SIGN);
  const sample = Array.from({ length: Math.min(MAX_IMG_PER_SIGN, perSeq.length) }, (_, i) => perSeq[Math.floor(i * step)]);
  return { total_visible: rows.length, sequences: bySeq.size, sample };
}

async function download(url, file) {
  try { await access(file); return; } catch {}
  const res = await fetch(url, { signal: AbortSignal.timeout(60_000) });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${file}`);
  await writeFile(file, Buffer.from(await res.arrayBuffer()));
}

// ---------- main ----------

await mkdir(join(OUT_DIR, "images"), { recursive: true });
const ways = await fetchTranPhu();
const { axis, lines, axisLen } = centerline(ways);
console.log(`Trần Phú trong vùng PTIT: ${ways.length} way, trục dài ${Math.round(axisLen)} m`);

const allSigns = parseCsv(await readFile(SIGNS_CSV, "utf8"));
const { start, inWindow, chosen } = pickSigns(allSigns, axis, lines, axisLen);
const at = (m) => { let acc = 0; for (let i = 1; i < axis.length; i++) { const l = dist(axis[i], axis[i - 1]); if (acc + l >= m) { const t = (m - acc) / l; return [axis[i - 1][0] + t * (axis[i][0] - axis[i - 1][0]), axis[i - 1][1] + t * (axis[i][1] - axis[i - 1][1])]; } acc += l; } return axis.at(-1); };
const unproj = ([x, y]) => [+(x / kx + lon0).toFixed(6), +(y / ky + lat0).toFixed(6)];
const segment = { from: unproj(at(start)), to: unproj(at(start + WINDOW_M)), start_m: start, signs_in_window: inWindow };
console.log(`Cửa sổ 500 m tốt nhất bắt đầu ở mét ${start}: ${inWindow} biển Mapillary trong ${NEAR_ROAD_M} m quanh đường, chọn ${chosen.length}`);
console.log(`  từ ${segment.from.join(", ")} đến ${segment.to.join(", ")}`);

const signRows = chosen.map((s) => ({ id: s.id, type: s.object_value, first_seen: s.first_seen, last_seen: s.last_seen, lon: s.lon, lat: s.lat, along_m: Math.round(s.along - start), road_dist_m: +s.roadDist.toFixed(1) }));
const toCsv = (rows) => "﻿" + [Object.keys(rows[0]).join(","), ...rows.map((r) => Object.values(r).join(","))].join("\n");
await writeFile(join(OUT_DIR, "signs.csv"), toCsv(signRows));
await writeFile(join(OUT_DIR, "segment.json"), JSON.stringify(segment, null, 2));
for (const r of signRows) console.log(`  ${String(r.along_m).padStart(4)} m  ${r.type}  (${r.first_seen} → ${r.last_seen})`);

if (!TOKEN) { console.log("\nChưa có MAPILLARY_TOKEN: dừng sau bước chọn biển. Kết quả: tools/poc/out/signs.csv"); process.exit(0); }

const perSign = await pool(chosen, 4, imagesAround);
const imgRows = perSign.flatMap((p) => p.sample);
console.log(`\n${imgRows.length} ảnh mẫu cho ${chosen.length} biển; đang tải thumbnail...`);
let done = 0;
await pool(imgRows, 6, async (r) => { await download(r.url, join(OUT_DIR, "images", `${r.image_id}.jpg`)); if (++done % 50 === 0) console.log(`  ${done}/${imgRows.length}`); });
await writeFile(join(OUT_DIR, "images.csv"), toCsv(imgRows.map(({ url, ...r }) => r)));

const share = (xs, f) => Math.round((100 * xs.filter(f).length) / Math.max(1, xs.length));
console.log(`\nMỗi biển (trung vị): ${perSign.map((p) => p.sequences).sort((a, b) => a - b)[Math.floor(perSign.length / 2)]} chuỗi ảnh nhìn được biển`);
console.log(`Ảnh 360°: ${share(imgRows, (r) => r.pano)}%  ·  Mapillary đã thấy biển trên ${share(imgRows, (r) => r.mapillary_saw)}% ảnh mẫu`);
console.log(`Kết quả: tools/poc/out/images.csv + images/*.jpg`);
