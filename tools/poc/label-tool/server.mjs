// Công cụ gán tay 3 nhãn cho ảnh đi qua biển (Node >= 18, không cần cài gói).
//   node tools/poc/label-tool/server.mjs      -> mở http://localhost:8787
// Đọc tools/poc/out/images.csv + images/*.jpg, ghi nhãn vào tools/poc/out/labels.csv sau mỗi lần bấm.
// Mỗi dòng nhãn: sign_id, image_id, labeler, label (seen | not_seen | unknown), labeled_at.

import { createServer } from "node:http";
import { readFile, writeFile, access } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, "..", "out");
const PORT = +(process.env.PORT || 8787);
const LABELS = new Set(["seen", "not_seen", "unknown"]);

function parseCsv(text) {
  const [head, ...rows] = text.replace(/^﻿/, "").trim().split(/\r?\n/);
  const cols = head.split(",");
  return rows.filter(Boolean).map((l) => Object.fromEntries(l.split(",").map((v, i) => [cols[i], v])));
}

const items = parseCsv(await readFile(join(OUT, "images.csv"), "utf8")).map((r) => ({
  sign_id: r.sign_id, sign_type: r.sign_type, image_id: r.image_id, captured: r.captured, creator: r.creator,
  dist_m: +r.dist_m, off_deg: +r.off_deg, pano: r.pano === "true", focal: +r.focal || 0.8, mapillary_saw: r.mapillary_saw === "true",
}));
// Nhóm theo biển, trong mỗi biển xếp từ gần tới xa: người gán nhìn ảnh rõ trước rồi mới tới ảnh khó.
items.sort((a, b) => a.sign_id.localeCompare(b.sign_id) || a.dist_m - b.dist_m);

const labelsFile = join(OUT, "labels.csv");
const key = (l) => `${l.sign_id}|${l.image_id}|${l.labeler}`;
const labels = new Map();
try { await access(labelsFile); for (const l of parseCsv(await readFile(labelsFile, "utf8"))) labels.set(key(l), l); } catch {}

let writing = Promise.resolve();
const saveLabels = () => (writing = writing.then(() => writeFile(labelsFile,
  "﻿sign_id,image_id,labeler,label,labeled_at\n" + [...labels.values()].map((l) => [l.sign_id, l.image_id, l.labeler, l.label, l.labeled_at].join(",")).join("\n"))));

const send = (res, code, body, type = "application/json; charset=utf-8") => {
  res.writeHead(code, { "Content-Type": type, "Cache-Control": "no-store" });
  res.end(typeof body === "string" || Buffer.isBuffer(body) ? body : JSON.stringify(body));
};

createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  try {
    if (req.method === "GET" && url.pathname === "/") return send(res, 200, await readFile(join(HERE, "index.html")), "text/html; charset=utf-8");
    if (req.method === "GET" && url.pathname === "/api/items") {
      const who = url.searchParams.get("labeler") ?? "";
      return send(res, 200, items.map((it) => ({ ...it, label: labels.get(key({ ...it, labeler: who }))?.label ?? null })));
    }
    const img = url.pathname.match(/^\/img\/(\d+)\.jpg$/);
    if (req.method === "GET" && img) return send(res, 200, await readFile(join(OUT, "images", `${img[1]}.jpg`)), "image/jpeg");
    if (req.method === "POST" && url.pathname === "/api/label") {
      let body = ""; for await (const c of req) body += c;
      const { sign_id, image_id, labeler, label } = JSON.parse(body);
      const who = String(labeler ?? "").replace(/[,\r\n]/g, " ").trim();
      if (!who || !items.some((it) => it.sign_id === sign_id && it.image_id === image_id)) return send(res, 400, { error: "bad item or labeler" });
      if (label === null) labels.delete(key({ sign_id, image_id, labeler: who }));
      else if (LABELS.has(label)) labels.set(key({ sign_id, image_id, labeler: who }), { sign_id, image_id, labeler: who, label, labeled_at: new Date().toISOString() });
      else return send(res, 400, { error: "bad label" });
      await saveLabels();
      return send(res, 200, { ok: true });
    }
    send(res, 404, { error: "not found" });
  } catch (e) {
    send(res, 500, { error: e.message });
  }
}).listen(PORT, "127.0.0.1", () => console.log(`Gán nhãn: http://localhost:${PORT}  (${items.length} ảnh, ${labels.size} nhãn đã có)`));
