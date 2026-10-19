// Kiểm tra độ phủ Mapillary theo từng con phố (Node >= 18, không cần cài thư viện).
//
//   $env:MAPILLARY_TOKEN = "MLY|..."      # PowerShell
//   node tools/mapillary-coverage/check-coverage.mjs
//   node tools/mapillary-coverage/check-coverage.mjs caugiay   # chỉ chạy 1 khu
//
// Kết quả: out/<khu>-streets.csv, out/<khu>-signs.csv, out/summary.json và bảng tóm tắt trên console.

import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const TOKEN = process.env.MAPILLARY_TOKEN;
if (!TOKEN) {
  console.error("Thiếu MAPILLARY_TOKEN. Lấy token tại https://www.mapillary.com/dashboard/developers");
  process.exit(1);
}

// bbox = [minLon, minLat, maxLon, maxLat], mỗi khu khoảng 1 km x 1 km. Sửa tuỳ ý.
const AREAS = {
  ptit:     { name: "Quanh Học viện CNBCVT (Km10 Nguyễn Trãi, Hà Đông)", bbox: [105.782, 20.976, 105.793, 20.986] },
  caugiay:  { name: "Cầu Giấy (Xuân Thủy – Trần Duy Hưng)",              bbox: [105.790, 21.026, 105.802, 21.038] },
  hoankiem: { name: "Hoàn Kiếm (quanh Hồ Gươm)",                          bbox: [105.845, 21.022, 105.857, 21.034] },
};

const SNAP_M = 20;          // ảnh cách tim đường quá 20 m thì bỏ
const BIN_M = 25;           // chia mỗi con phố thành đoạn 25 m để đo độ phủ
const CELL_DEG = 0.001;     // ô truy vấn ban đầu (~110 m); tự chia nhỏ nếu chạm giới hạn 2000 hoặc bị báo quá nhiều dữ liệu
const API_LIMIT = 2000;
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), "out");

// ---------- HTTP ----------

async function getJson(url, init = {}, tries = 5) {
  for (let i = 0; ; i++) {
    try {
      const res = await fetch(url, { ...init, signal: AbortSignal.timeout(90_000) });
      if (res.ok) return await res.json();
      const body = await res.text();
      // Ô quá dày ảnh: thử lại vô ích, để fetchAll chia nhỏ ô.
      if (/reduce the amount of data/i.test(body)) throw Object.assign(new Error("TOO_MUCH_DATA"), { fatal: true, tooMuch: true });
      if (res.status === 429 || res.status >= 500) throw new Error(`HTTP ${res.status}: ${body.slice(0, 200)}`);
      throw Object.assign(new Error(`HTTP ${res.status}: ${body.slice(0, 300)}`), { fatal: true });
    } catch (e) {
      if (e.fatal || i >= tries - 1) throw e;
      await new Promise((r) => setTimeout(r, 1000 * 2 ** i));
    }
  }
}

async function pool(items, n, fn) {
  const out = [];
  let next = 0;
  await Promise.all(Array.from({ length: n }, async () => {
    while (next < items.length) { const i = next++; out[i] = await fn(items[i]); }
  }));
  return out;
}

function grid([x0, y0, x1, y1], step) {
  const cells = [];
  for (let x = x0; x < x1; x += step)
    for (let y = y0; y < y1; y += step)
      cells.push([x, y, Math.min(x + step, x1), Math.min(y + step, y1)]);
  return cells;
}

// Graph API không phân trang theo bbox: nếu trả đủ 2000 bản ghi thì chia ô làm 4 và hỏi lại.
async function fetchAll(endpoint, fields, bbox, extra = "") {
  const url = `https://graph.mapillary.com/${endpoint}?fields=${fields}&bbox=${bbox.map((v) => v.toFixed(6)).join(",")}&limit=${API_LIMIT}${extra}`;
  const small = bbox[2] - bbox[0] < 0.0002;
  let data;
  try {
    ({ data = [] } = await getJson(url, { headers: { Authorization: `OAuth ${TOKEN}` } }));
  } catch (e) {
    if (!e.tooMuch) throw e;
    if (small) { console.warn(`  ! Bỏ qua ô quá dày ${bbox.map((v) => v.toFixed(4)).join(",")}`); return []; }
    data = null;
  }
  if (data && (data.length < API_LIMIT || small)) return data;
  const [x0, y0, x1, y1] = bbox, mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
  const parts = await Promise.all([[x0, y0, mx, my], [mx, y0, x1, my], [x0, my, mx, y1], [mx, my, x1, y1]]
    .map((b) => fetchAll(endpoint, fields, b, extra)));
  return parts.flat();
}

async function fetchCells(endpoint, fields, bbox) {
  const chunks = await pool(grid(bbox, CELL_DEG), 4, (c) => fetchAll(endpoint, fields, c));
  const byId = new Map();
  for (const d of chunks.flat()) byId.set(d.id, d);
  return [...byId.values()];
}

async function fetchRoads([w, s, e, n]) {
  const q = `[out:json][timeout:60];
way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service)(_link)?$"](${s},${w},${n},${e});
out geom tags;`;
  // Máy chủ chính hay quá tải (504) nên thử lần lượt các mirror.
  const mirrors = ["https://overpass-api.de", "https://overpass.private.coffee", "https://overpass.kumi.systems"];
  let lastErr;
  for (const m of mirrors) {
    try {
      const res = await getJson(`${m}/api/interpreter`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded", "User-Agent": "doan-mapillary-coverage/1.0" },
        body: "data=" + encodeURIComponent(q),
      }, 2);
      return res.elements.filter((el) => el.geometry?.length >= 2);
    } catch (e) { lastErr = e; }
  }
  throw lastErr;
}

// ---------- Hình học (phép chiếu phẳng cục bộ, đủ chính xác cho 1 km) ----------

function projector([w, s, e, n]) {
  const lat0 = (s + n) / 2, lon0 = (w + e) / 2, kx = Math.cos((lat0 * Math.PI) / 180) * 111320, ky = 110540;
  return (lon, lat) => [(lon - lon0) * kx, (lat - lat0) * ky];
}

function nearestOnSegment(p, a, b) {
  const dx = b[0] - a[0], dy = b[1] - a[1], len2 = dx * dx + dy * dy;
  const t = len2 ? Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2)) : 0;
  const qx = a[0] + t * dx, qy = a[1] + t * dy;
  return { d: Math.hypot(p[0] - qx, p[1] - qy), t };
}

// ---------- Phân tích ----------

async function analyse(key) {
  const area = AREAS[key];
  console.log(`\n=== ${area.name} ===`);
  const proj = projector(area.bbox);

  const [images, roads, features] = await Promise.all([
    fetchCells("images", "id,captured_at,sequence,geometry,is_pano", area.bbox),
    fetchRoads(area.bbox).catch((e) => {
      console.warn(`  ! Không lấy được đường OSM (${e.message.slice(0, 80)}) → tính theo ô lưới ${BIN_M} m`);
      return null;
    }),
    fetchCells("map_features", "id,object_value,first_seen_at,last_seen_at,geometry", area.bbox),
  ]);
  console.log(`  ${images.length} ảnh, ${roads?.length ?? 0} đoạn đường OSM, ${features.length} map feature`);
  if (!roads) return gridOnly(key, area, images, proj);

  // Gom các way OSM cùng tên thành 1 con phố; chia mỗi đoạn thẳng thành bin 25 m.
  const streets = new Map();
  const segs = [];
  for (const way of roads) {
    const name = way.tags.name || `(không tên) way/${way.id}`;
    if (!streets.has(name)) streets.set(name, { name, highway: way.tags.highway, lengthM: 0, bins: [], images: [] });
    const st = streets.get(name);
    const pts = way.geometry.map((g) => proj(g.lon, g.lat));
    for (let i = 1; i < pts.length; i++) {
      const len = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
      const nBins = Math.max(1, Math.round(len / BIN_M));
      const firstBin = st.bins.length;
      for (let k = 0; k < nBins; k++) st.bins.push({ years: new Set(), days: new Set(), seqs: new Set() });
      segs.push({ a: pts[i - 1], b: pts[i], st, firstBin, nBins });
      st.lengthM += len;
    }
  }

  // Chỉ mục lưới 50 m cho các đoạn thẳng.
  const G = 50, index = new Map();
  const cellKey = (x, y) => `${Math.floor(x / G)},${Math.floor(y / G)}`;
  for (const s of segs) {
    const [xa, xb] = [Math.min(s.a[0], s.b[0]) - SNAP_M, Math.max(s.a[0], s.b[0]) + SNAP_M];
    const [ya, yb] = [Math.min(s.a[1], s.b[1]) - SNAP_M, Math.max(s.a[1], s.b[1]) + SNAP_M];
    for (let x = Math.floor(xa / G); x <= Math.floor(xb / G); x++)
      for (let y = Math.floor(ya / G); y <= Math.floor(yb / G); y++) {
        const k = `${x},${y}`;
        if (!index.has(k)) index.set(k, []);
        index.get(k).push(s);
      }
  }

  let snapped = 0;
  const allYears = new Set();
  for (const img of images) {
    if (!img.geometry || !img.captured_at) continue;
    const p = proj(...img.geometry.coordinates);
    let best = null;
    for (const s of index.get(cellKey(...p)) || []) {
      const r = nearestOnSegment(p, s.a, s.b);
      if (r.d <= SNAP_M && (!best || r.d < best.d)) best = { ...r, s };
    }
    if (!best) continue;
    snapped++;
    const date = new Date(img.captured_at).toISOString().slice(0, 10), year = date.slice(0, 4);
    allYears.add(year);
    const { s, t } = best;
    const bin = s.st.bins[s.firstBin + Math.min(s.nBins - 1, Math.floor(t * s.nBins))];
    bin.years.add(year); bin.days.add(date); bin.seqs.add(img.sequence);
    s.st.images.push({ year, date, seq: img.sequence });
  }

  const rows = [...streets.values()].map((st) => {
    const nb = st.bins.length || 1;
    const covered = st.bins.filter((b) => b.days.size >= 1).length;
    const multiYear = st.bins.filter((b) => b.years.size >= 2).length;
    const multiDay3 = st.bins.filter((b) => b.days.size >= 3).length;
    const years = [...new Set(st.images.map((i) => i.year))].sort();
    return {
      street: st.name,
      highway: st.highway,
      length_m: Math.round(st.lengthM),
      images: st.images.length,
      sequences: new Set(st.images.map((i) => i.seq)).size,
      capture_days: new Set(st.images.map((i) => i.date)).size,
      years: years.join(" "),
      latest: st.images.reduce((m, i) => (i.date > m ? i.date : m), ""),
      pct_covered: Math.round((100 * covered) / nb),
      pct_2plus_years: Math.round((100 * multiYear) / nb),
      pct_3plus_days: Math.round((100 * multiDay3) / nb),
    };
  }).sort((a, b) => b.length_m - a.length_m);

  // Biển báo Mapillary đã tự phát hiện (nếu có) — hữu ích làm dữ liệu so sánh.
  const signs = features
    .filter((f) => /^(regulatory|warning|information|complementary)--/.test(f.object_value || ""))
    .map((f) => ({
      id: f.id,
      object_value: f.object_value,
      first_seen: f.first_seen_at?.slice(0, 10) ?? "",
      last_seen: f.last_seen_at?.slice(0, 10) ?? "",
      lon: f.geometry?.coordinates[0], lat: f.geometry?.coordinates[1],
    }));

  // Tổng hợp theo chiều dài đường (đường dài có trọng số lớn hơn).
  const totLen = rows.reduce((s, r) => s + r.length_m, 0) || 1;
  const w = (k) => Math.round(rows.reduce((s, r) => s + r[k] * r.length_m, 0) / totLen);
  const summary = {
    area: area.name,
    bbox: area.bbox,
    images_total: images.length,
    images_on_roads: snapped,
    sequences_total: new Set(images.map((i) => i.sequence)).size,
    years: [...allYears].sort(),
    road_length_km: +(totLen / 1000).toFixed(1),
    pct_road_covered: w("pct_covered"),
    pct_road_2plus_years: w("pct_2plus_years"),
    pct_road_3plus_days: w("pct_3plus_days"),
    mapillary_signs: signs.length,
    signs_seen_over_1y: signs.filter((s) => s.first_seen && s.last_seen &&
      new Date(s.last_seen) - new Date(s.first_seen) > 365 * 864e5).length,
  };

  await writeFile(join(OUT_DIR, `${key}-streets.csv`), toCsv(rows));
  await writeFile(join(OUT_DIR, `${key}-signs.csv`), toCsv(signs));

  console.log(`  Năm chụp: ${summary.years.join(", ") || "—"}`);
  console.log(`  Đường được phủ: ${summary.pct_road_covered}% | ≥2 năm: ${summary.pct_road_2plus_years}% | ≥3 ngày chụp: ${summary.pct_road_3plus_days}%`);
  console.log(`  Biển báo Mapillary phát hiện: ${summary.mapillary_signs} (thấy qua >1 năm: ${summary.signs_seen_over_1y})`);
  console.table(rows.filter((r) => !r.street.startsWith("(không tên)")).slice(0, 12)
    .map(({ street, length_m, images, capture_days, years, pct_covered, pct_2plus_years }) =>
      ({ street, length_m, images, capture_days, years, pct_covered, pct_2plus_years })));
  return summary;
}

// Phương án dự phòng khi Overpass lỗi: chỉ xét các ô 25 m có ảnh (ô có ảnh ≈ nằm trên đường đã chụp).
async function gridOnly(key, area, images, proj) {
  const cells = new Map();
  const allYears = new Set();
  for (const img of images) {
    if (!img.geometry || !img.captured_at) continue;
    const [x, y] = proj(...img.geometry.coordinates);
    const k = `${Math.floor(x / BIN_M)},${Math.floor(y / BIN_M)}`;
    if (!cells.has(k)) cells.set(k, { years: new Set(), days: new Set(), seqs: new Set(), n: 0 });
    const c = cells.get(k), date = new Date(img.captured_at).toISOString().slice(0, 10);
    c.years.add(date.slice(0, 4)); c.days.add(date); c.seqs.add(img.sequence); c.n++;
    allYears.add(date.slice(0, 4));
  }
  const rows = [...cells.entries()].map(([cell, c]) => ({
    cell, images: c.n, sequences: c.seqs.size, capture_days: c.days.size, years: [...c.years].sort().join(" "),
  }));
  const pct = (f) => Math.round((100 * rows.filter(f).length) / (rows.length || 1));
  const summary = {
    area: area.name, bbox: area.bbox, mode: "grid-only (không có dữ liệu đường OSM)",
    images_total: images.length,
    sequences_total: new Set(images.map((i) => i.sequence)).size,
    years: [...allYears].sort(),
    cells_with_images: rows.length,
    pct_cells_2plus_years: pct((r) => r.years.includes(" ")),
    pct_cells_3plus_days: pct((r) => r.capture_days >= 3),
  };
  await writeFile(join(OUT_DIR, `${key}-cells.csv`), toCsv(rows));
  console.log(`  Năm chụp: ${summary.years.join(", ") || "—"}`);
  console.log(`  ${rows.length} ô ${BIN_M} m có ảnh | trong đó ≥2 năm: ${summary.pct_cells_2plus_years}% | ≥3 ngày: ${summary.pct_cells_3plus_days}%`);
  console.log("  (Tỉ lệ này tính trên phần ĐÃ được chụp, không phải trên toàn bộ đường — chạy lại khi Overpass ổn để có số theo phố.)");
  return summary;
}

function toCsv(rows) {
  if (!rows.length) return "";
  const cols = Object.keys(rows[0]);
  const esc = (v) => (/[",\n]/.test(String(v ?? "")) ? `"${String(v).replace(/"/g, '""')}"` : String(v ?? ""));
  return "﻿" + [cols.join(","), ...rows.map((r) => cols.map((c) => esc(r[c])).join(","))].join("\n");
}

// ---------- main ----------

await mkdir(OUT_DIR, { recursive: true });
const keys = process.argv.slice(2).length ? process.argv.slice(2) : Object.keys(AREAS);
const summaries = [];
for (const k of keys) {
  if (!AREAS[k]) { console.error(`Không có khu "${k}". Chọn: ${Object.keys(AREAS).join(", ")}`); continue; }
  summaries.push(await analyse(k));
}
await writeFile(join(OUT_DIR, "summary.json"), JSON.stringify(summaries, null, 2));

console.log("\nGợi ý đọc kết quả (ngưỡng tham khảo, không phải chuẩn):");
console.log("  ≥2 năm trên >30% chiều dài đường  → đủ để thử phát hiện thay đổi (biển mới/biển bị gỡ).");
console.log("  ≥3 ngày chụp trên >30% chiều dài  → đủ để thử đồng thuận nhiều lượt quan sát.");
console.log("  Thấp hơn nhiều                     → coi Mapillary là dữ liệu phụ, nhóm tự quay video là nguồn chính.");
console.log(`Chi tiết: ${OUT_DIR}`);
