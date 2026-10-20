"""Lượt 2: đối chiếu ảnh của một trip với biển trong snapshot cùng năm → JSONL not_seen/unknown.

Chạy từ gốc repo:
    python ai/scripts/pass2_not_seen.py --trip data/mapillary/<sequence_id> \
        --signs data/snapshots/signs-2025.json --observations data/observations/mly-seq_<id>.jsonl

`--signs`: response `GET /signs?bbox=...&evaluation_year=<năm chụp>` (api.md), có `items`
và `snapshot_run_id`. `--checks`: CSV image_id, sign_id, sharp, unoccluded từ bộ kiểm độc lập;
thiếu thì hai cờ là null nên không có not_seen. `--params`: bản params riêng của môi trường
mô phỏng (bật negative_evidence_enabled); mặc định đọc config/params.yaml.

Ra data/observations/<trip_id>.pass2.jsonl. Gửi bằng POST /observations/batch như lượt 1.
"""

import argparse
import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from livesign_ai.config import PARAMS_YAML, REPO, load_params, param  # noqa: E402
from livesign_ai.observation import validate_row  # noqa: E402
from livesign_ai.pass2 import pass2_rows  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def parse_bool(v):
    v = (v or "").strip().lower()
    return True if v == "true" else False if v == "false" else None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--trip", required=True, type=Path)
    ap.add_argument("--signs", required=True, type=Path)
    ap.add_argument("--observations", required=True, type=Path, help="JSONL lượt 1 của cùng trip")
    ap.add_argument("--checks", type=Path)
    ap.add_argument("--params", type=Path, default=PARAMS_YAML)
    args = ap.parse_args()

    from PIL import Image, ImageOps

    params = load_params(args.params)
    negative = param(params, "workflow.negative_evidence_enabled")
    snap = json.loads(args.signs.read_text(encoding="utf-8"))
    run_id = snap.get("snapshot_run_id")
    if not run_id:
        sys.exit("--signs thiếu snapshot_run_id; lấy đúng response của API 1.2")

    images = read_jsonl(args.trip / "images.jsonl")
    sizes = {}
    for im in images:
        p = args.trip / "images" / f"{im['id']}.jpg"
        if p.exists():
            sizes[str(im["id"])] = ImageOps.exif_transpose(Image.open(p)).size
    detected = {}
    for r in read_jsonl(args.observations):
        if r["outcome"] == "seen":
            detected.setdefault(r["image_id"], set()).add(r["sign_type_code"])
    checks = {}
    if args.checks:
        with open(args.checks, encoding="utf-8-sig") as f:
            for r in csv.DictReader(f):
                checks[(r["image_id"], r["sign_id"])] = {"sharp": parse_bool(r.get("sharp")),
                                                         "unoccluded": parse_bool(r.get("unoccluded"))}

    rows = pass2_rows(images, sizes, snap["items"], detected, snapshot_run_id=run_id, negative_enabled=negative,
                      keep=param(params, "geometry.perspective_frame_keep"),
                      max_dist_m=param(params, "geometry.visibility_max_distance_m"),
                      max_facing_diff_deg=param(params, "visibility.max_facing_diff_deg"), checks=checks)
    bad = [(r["observation_id"], e) for r in rows for e in validate_row(r)]
    if bad:
        sys.exit(f"{len(bad)} lỗi hợp đồng, ví dụ: {bad[:3]}")
    trip_id = rows[0]["trip_id"] if rows else f"mly-seq:{args.trip.name}"
    out = REPO / "data" / "observations" / f"{trip_id.replace(':', '_')}.pass2.jsonl"
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    n_neg = sum(r["outcome"] == "not_seen" for r in rows)
    print(f"{trip_id}: {len(rows)} cặp kiểm, {n_neg} not_seen (negative_evidence_enabled={negative}) → {out}")
    if negative is not True:
        print("negative_evidence_enabled chưa bật: mọi dòng là unknown, server sẽ từ chối not_seen")


if __name__ == "__main__":
    main()
