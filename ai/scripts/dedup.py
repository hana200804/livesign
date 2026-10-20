"""Tính sha256 + pHash cho mọi ảnh trong data/raw/ và xuất cặp gần trùng để duyệt.

Chạy từ gốc repo:
    python ai/scripts/dedup.py

Ra trong data/processed/dedup/:
- hashes.csv: image_key, path, sha256, phash64 (hex), width, height, exif_rotated
- candidates.csv: cặp Hamming ≤ training.phash_max_hamming, kèm cột `same` để trống
- review/<số>.jpg: hai ảnh cạnh nhau cho từng cặp

Người duyệt điền `same` = yes/no trong candidates.csv. Cặp còn trống được build_dataset.py
coi như cùng nhóm (an toàn hơn cho việc chống rò test), nhưng phải duyệt trước khi chốt.
"""

import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from PIL import Image, ImageOps  # noqa: E402

from livesign_ai.config import REPO, file_sha256, load_params, load_yaml, param  # noqa: E402
from livesign_ai.dataset import read_yolo_source  # noqa: E402
from livesign_ai.dedup import candidate_pairs, phash_int  # noqa: E402

RAW = REPO / "data" / "raw"
sys.stdout.reconfigure(encoding="utf-8")  # console Windows mặc định cp1252
OUT = REPO / "data" / "processed" / "dedup"


def load_existing(path):
    if not path.exists():
        return {}
    with open(path, encoding="utf-8") as f:
        return {r["path"]: r for r in csv.DictReader(f)}


def side_by_side(a, b, height=320):
    ims = []
    for p in (a, b):
        im = ImageOps.exif_transpose(Image.open(p)).convert("RGB")
        im.thumbnail((10_000, height))
        ims.append(im)
    out = Image.new("RGB", (ims[0].width + ims[1].width + 8, height), (255, 0, 255))
    out.paste(ims[0], (0, 0))
    out.paste(ims[1], (ims[0].width + 8, 0))
    return out


def main():
    max_d = param(load_params(), "training.phash_max_hamming")
    OUT.mkdir(parents=True, exist_ok=True)
    hashes_csv = OUT / "hashes.csv"
    cache = load_existing(hashes_csv)
    rows = []
    for source in sorted(p.name for p in RAW.iterdir() if p.is_dir()):
        for rec in read_yolo_source(source, RAW / source, load_yaml):
            rel = rec.image_path.relative_to(REPO).as_posix()
            if rel in cache:
                rows.append(cache[rel])
                continue
            raw = Image.open(rec.image_path)
            rotated = raw.getexif().get(0x0112, 1) not in (1, None)
            img = ImageOps.exif_transpose(raw)
            rows.append({"image_key": rec.image_key, "path": rel, "sha256": file_sha256(rec.image_path),
                         "phash64": f"{phash_int(img):016x}", "width": img.width, "height": img.height,
                         "exif_rotated": int(rotated)})
    with open(hashes_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)

    pairs = candidate_pairs([(r["image_key"], int(r["phash64"], 16)) for r in rows], max_d)
    path_of = {r["image_key"]: REPO / r["path"] for r in rows}
    old = {}
    cand_csv = OUT / "candidates.csv"
    if cand_csv.exists():
        with open(cand_csv, encoding="utf-8") as f:
            old = {(r["key_a"], r["key_b"]): r["same"] for r in csv.DictReader(f)}
    review = OUT / "review"
    review.mkdir(exist_ok=True)
    with open(cand_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["n", "key_a", "key_b", "hamming", "same"])
        for n, (a, b, d) in enumerate(pairs, 1):
            w.writerow([n, a, b, d, old.get((a, b), "")])
            sheet = review / f"{n:05d}.jpg"
            if not sheet.exists():
                side_by_side(path_of[a], path_of[b]).save(sheet, quality=85)
    print(f"{len(rows)} ảnh, {len(pairs)} cặp Hamming ≤{max_d} → {cand_csv}")


if __name__ == "__main__":
    main()
