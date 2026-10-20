"""Gộp các nguồn trong data/raw/ thành dataset YOLO 12 lớp + manifest.

Cần chạy dedup.py trước. Chạy từ gốc repo:
    python ai/scripts/build_dataset.py --rule vnts_kaggle=by_group:0.8,0.1,0.1 --version r001

Quy tắc chia mỗi nguồn: `keep` (giữ split gốc), `train|val|test` (cả nguồn), hoặc
`by_group:<train>,<val>,<test>`. Nguồn có `sources.<nguồn>.mapping` rỗng bị bỏ qua.

Ra data/processed/yolo/{images,labels}/{train,val,test} và data/processed/manifest-<version>.json.
"""

import argparse
import csv
import hashlib
import json
import shutil
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from PIL import Image, ImageOps  # noqa: E402

from livesign_ai.config import (CLASSES_YAML, DATASET_YAML, PARAMS_YAML, REPO, code_to_id,  # noqa: E402
                                load_classes, load_yaml)
from livesign_ai.dataset import map_record, read_yolo_source  # noqa: E402
from livesign_ai.dedup import duplicate_groups, exact_duplicate_pairs  # noqa: E402
from livesign_ai.manifest import build_manifest, label_mapping_rows  # noqa: E402
from livesign_ai.split import SPLITS, assign_splits, groups_cross_splits  # noqa: E402

RAW = REPO / "data" / "raw"
sys.stdout.reconfigure(encoding="utf-8")  # console Windows mặc định cp1252
PROCESSED = REPO / "data" / "processed"
YOLO = PROCESSED / "yolo"
DEDUP = PROCESSED / "dedup"


def parse_rule(text):
    src, rule = text.split("=", 1)
    if rule.startswith("by_group:"):
        tr, va, te = (float(x) for x in rule.split(":", 1)[1].split(","))
        return src, {"by_group": {"train": tr, "val": va, "test": te}}
    return src, rule


def read_csv(path):
    with open(path, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--rule", action="append", required=True, help="nguồn=quy_tắc, lặp cho từng nguồn")
    ap.add_argument("--version", required=True, help="hậu tố manifest, vd. r001")
    args = ap.parse_args()
    rules = dict(parse_rule(r) for r in args.rule)

    classes_cfg = load_classes()
    ids = code_to_id(classes_cfg)
    if load_yaml(DATASET_YAML)["names"] != classes_cfg["classes"]:
        sys.exit("dataset.yaml lệch classes.yaml, sửa trước khi build")

    hashes = {r["image_key"]: r for r in read_csv(DEDUP / "hashes.csv")}
    candidates = read_csv(DEDUP / "candidates.csv")
    unreviewed = sum(1 for c in candidates if c["same"].strip().lower() not in ("yes", "no")
                     and hashes[c["key_a"]]["sha256"] != hashes[c["key_b"]]["sha256"])
    same = [(c["key_a"], c["key_b"]) for c in candidates if c["same"].strip().lower() != "no"]

    recs, mapped, dropped, sources, exclusions = [], {}, Counter(), {}, []
    for source, rule in rules.items():
        src_cfg = classes_cfg["sources"].get(source)
        if not src_cfg or not src_cfg.get("mapping"):
            sys.exit(f"{source}: chưa có mapping trong classes.yaml")
        meta = RAW / source / "SOURCE.json"
        sources[source] = json.loads(meta.read_text(encoding="utf-8")) if meta.exists() else {"source_id": source}
        ignore = set(src_cfg.get("ignore") or [])
        for rec in read_yolo_source(source, RAW / source, load_yaml):
            if rec.image_key not in hashes:
                sys.exit(f"{rec.image_key}: chưa có trong hashes.csv, chạy lại dedup.py")
            kept, d = map_record(rec, src_cfg["mapping"], ignore, ids)
            for label, n in d.items():
                dropped[(source, label)] += n
            if kept is None:
                exclusions.append({"image_key": rec.image_key, "reason": "có nhãn chưa ánh xạ, có thể lẫn lớp đích"})
                continue
            recs.append(rec)
            mapped[rec.image_key] = kept

    keys = [r.image_key for r in recs]
    exact = exact_duplicate_pairs({k: hashes[k]["sha256"] for k in keys})
    for _, dup in exact:
        exclusions.append({"image_key": dup, "reason": "trùng sha256 với ảnh khác"})
    exact_dups = {dup for _, dup in exact}
    keyset = set(keys)
    groups = duplicate_groups(keys, exact + [(a, b) for a, b in same if a in keyset and b in keyset])
    recs = [r for r in recs if r.image_key not in exact_dups]

    items = [{"key": r.image_key, "source": r.source, "orig_split": r.orig_split, "group_id": groups[r.image_key]}
             for r in recs]
    split_of, split_exceptions = assign_splits(items, rules)
    assert not groups_cross_splits(split_of, groups)

    if YOLO.exists():
        shutil.rmtree(YOLO)
    images, exported = [], []
    for rec in recs:
        split = split_of[rec.image_key]
        name = f"{rec.source}__{rec.image_path.stem}"
        img_dst = YOLO / "images" / split / f"{name}{rec.image_path.suffix.lower()}"
        lab_dst = YOLO / "labels" / split / f"{name}.txt"
        img_dst.parent.mkdir(parents=True, exist_ok=True)
        lab_dst.parent.mkdir(parents=True, exist_ok=True)
        h = hashes[rec.image_key]
        if int(h["exif_rotated"]):
            ImageOps.exif_transpose(Image.open(rec.image_path)).save(img_dst)
        else:
            shutil.copy2(rec.image_path, img_dst)
        objs = mapped[rec.image_key]
        lab_dst.write_text("".join(f"{cid} {cx:.6f} {cy:.6f} {w:.6f} {hh:.6f}\n" for cid, (cx, cy, w, hh) in objs),
                           encoding="utf-8")
        exported.append((split, [cid for cid, _ in objs]))
        images.append({"image_key": rec.image_key, "source_image_id": rec.image_path.stem, "sha256": h["sha256"],
                       "phash64": h["phash64"], "duplicate_group_id": groups[rec.image_key],
                       "source_sequence_id": None, "split": split, "orig_split": rec.orig_split,
                       "annotation_ref": rec.label_path.relative_to(REPO).as_posix() if rec.label_path else None})

    cfg_hash = hashlib.sha256(CLASSES_YAML.read_bytes() + PARAMS_YAML.read_bytes()).hexdigest()
    manifest = build_manifest(
        manifest_version=f"livesign-dataset-{args.version}", classes_cfg=classes_cfg, sources=sources,
        label_mapping=label_mapping_rows({**classes_cfg, "sources": {s: classes_cfg["sources"][s] for s in rules}},
                                         dropped),
        images=images, exported=exported, split_rule=rules,
        exclusions={"images": exclusions, "split_exceptions": split_exceptions,
                    "unreviewed_candidate_pairs": unreviewed},
        config_sha256=cfg_hash)
    out = PROCESSED / f"manifest-{args.version}.json"
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"{len(images)} ảnh xuất, {len(exclusions)} ảnh loại, {len(split_exceptions)} nhóm chuyển split, "
          f"{unreviewed} cặp chưa duyệt → {out}")
    for code, per in manifest["object_count"].items():
        print(f"  {code:8s} " + "  ".join(f"{s}={per[s]}" for s in SPLITS))


if __name__ == "__main__":
    main()
