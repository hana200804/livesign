"""Kiểm kê nhãn gốc của dataset trong data/raw/: đếm object và cắt crop mẫu để xem.

Chạy từ gốc repo:
    python ai/scripts/inventory.py                      # mọi nguồn trong data/raw
    python ai/scripts/inventory.py --source vnts_kaggle --per-label 20

Ra data/inventory/<nguồn>/counts.csv và <nhãn>.jpg (lưới crop). Dùng để điền
`sources.<nguồn>.mapping` trong ai/configs/classes.yaml sau khi xem ảnh thật.
"""

import argparse
import csv
import random
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from PIL import Image, ImageDraw, ImageOps  # noqa: E402

from livesign_ai.boxes import yolo_to_xyxy  # noqa: E402
from livesign_ai.config import REPO, load_yaml  # noqa: E402
from livesign_ai.dataset import count_labels, read_yolo_source  # noqa: E402

RAW = REPO / "data" / "raw"
sys.stdout.reconfigure(encoding="utf-8")  # console Windows mặc định cp1252
OUT = REPO / "data" / "inventory"
CELL = 128


def crop_grid(samples, per_row=5):
    """`samples`: list (ảnh PIL, bbox px). Nới bbox 20% để thấy ngữ cảnh."""
    rows = (len(samples) + per_row - 1) // per_row
    grid = Image.new("RGB", (per_row * CELL, rows * CELL), (40, 40, 40))
    for i, (img, (x1, y1, x2, y2)) in enumerate(samples):
        pad_x, pad_y = (x2 - x1) * 0.2, (y2 - y1) * 0.2
        crop = img.crop((max(0, x1 - pad_x), max(0, y1 - pad_y), min(img.width, x2 + pad_x), min(img.height, y2 + pad_y)))
        crop.thumbnail((CELL, CELL))
        cell = Image.new("RGB", (CELL, CELL), (40, 40, 40))
        cell.paste(crop, ((CELL - crop.width) // 2, (CELL - crop.height) // 2))
        ImageDraw.Draw(cell).text((2, 2), str(i + 1), fill=(255, 255, 0))
        grid.paste(cell, ((i % per_row) * CELL, (i // per_row) * CELL))
    return grid


def safe_name(label):
    return re.sub(r"[^\w.-]+", "_", label).strip("_") or "label"


def run(source, per_label, seed):
    recs = read_yolo_source(source, RAW / source, load_yaml)
    out = OUT / source
    out.mkdir(parents=True, exist_ok=True)
    objects, images = count_labels(recs)
    with open(out / "counts.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["orig_label", "orig_split", "objects", "images"])
        for (label, split), n in sorted(objects.items()):
            w.writerow([label, split, n, images[(label, split)]])

    by_label = {}
    for rec in recs:
        for o in rec.objects:
            by_label.setdefault(o.orig_label, []).append((rec, o))
    rng = random.Random(seed)
    for label, items in sorted(by_label.items()):
        picked = rng.sample(items, min(per_label, len(items)))
        samples = []
        for rec, o in picked:
            img = ImageOps.exif_transpose(Image.open(rec.image_path)).convert("RGB")
            samples.append((img, yolo_to_xyxy(*o.yolo, img.width, img.height)))
        crop_grid(samples).save(out / f"{safe_name(label)}.jpg", quality=90)
    total = sum(objects.values())
    print(f"{source}: {len(recs)} ảnh, {total} object, {len(by_label)} nhãn → {out}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", action="append", help="tên thư mục trong data/raw; mặc định tất cả")
    ap.add_argument("--per-label", type=int, default=20)
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()
    sources = args.source or sorted(p.name for p in RAW.iterdir() if p.is_dir())
    for s in sources:
        run(s, args.per_label, args.seed)


if __name__ == "__main__":
    main()
