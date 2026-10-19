// Xếp hạng các ô ~200 m theo độ hợp làm đoạn demo (Node >= 18).
//   node rank-segments.mjs <khu>
// Đọc out/<khu>-trip-check.csv (từ trip-check.mjs) + out/<khu>-signs.csv (first_seen / last_seen của Mapillary).

import { readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const AREA = process.argv[2] || "hoankiem";
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), "out");
const CELL = 0.002; // ~200 m

const readCsv = async (f) => {
  const lines = (await readFile(join(OUT_DIR, f), "utf8")).replace(/^﻿/, "").trim().split("\n");
  const cols = lines[0].split(",");
  return lines.slice(1).map((l) => Object.fromEntries(l.split(",").map((v, i) => [cols[i], v])));
};

const trips = await readCsv(`${AREA}-trip-check.csv`);
const seen = new Map((await readCsv(`${AREA}-signs.csv`)).map((s) => [s.id, s]));

const cells = new Map();
for (const t of trips) {
  const s = seen.get(t.id) || {};
  const key = `${Math.floor(+t.lon / CELL)},${Math.floor(+t.lat / CELL)}`;
  if (!cells.has(key)) cells.set(key, { key, signs: 0, multiCreator: 0, multiYear: 0, gone: 0, fresh: 0, types: new Set(), lon: 0, lat: 0, passCreators: [] });
  const c = cells.get(key);
  c.signs++; c.lon += +t.lon; c.lat += +t.lat;
  c.types.add(t.type.split("--")[1]);
  c.passCreators.push(+t.pass_creators);
  if (+t.seen_creators >= 2) c.multiCreator++;
  if ((t.seen_years || "").includes(" ")) c.multiYear++;
  const last = (s.last_seen || "").slice(0, 4), first = (s.first_seen || "").slice(0, 4);
  if (last && last <= "2023" && +t.pass_creators >= 3) c.gone++;   // thấy lần cuối <= 2023 nhưng sau đó vẫn có nhiều người đi qua
  if (first && first >= "2024") c.fresh++;                          // chỉ xuất hiện từ 2024
}

const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)] ?? 0; };
const rows = [...cells.values()].filter((c) => c.signs >= 5).map((c) => ({
  lat: (c.lat / c.signs).toFixed(5), lon: (c.lon / c.signs).toFixed(5),
  signs: c.signs, types: c.types.size, multi_creator: c.multiCreator, multi_year: c.multiYear,
  maybe_removed: c.gone, maybe_new: c.fresh, median_pass_creators: median(c.passCreators),
  score: c.multiCreator + c.multiYear + 2 * Math.min(c.gone + c.fresh, 10) + c.types.size,
})).sort((a, b) => b.score - a.score);

await writeFile(join(OUT_DIR, `${AREA}-segments.csv`), "﻿" + [Object.keys(rows[0]).join(","), ...rows.map((r) => Object.values(r).join(","))].join("\n"));
console.table(rows.slice(0, 10));
