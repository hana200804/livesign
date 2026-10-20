"""Manifest dataset theo mục "Manifest dataset" của docs/ai-pipeline.md.

Số đếm tính từ object thực sự được xuất, không lấy từ trang giới thiệu của nguồn.
"""

from collections import Counter


def count_objects(exported, classes):
    """`exported`: list (split, [class_id, ...]). Trả về {mã: {split: số object}}."""
    c = Counter()
    for split, cids in exported:
        for cid in cids:
            c[(classes[cid], split)] += 1
    out = {code: {s: 0 for s in ("train", "val", "test")} for code in classes.values()}
    for (code, split), n in c.items():
        out[code][split] = n
    return out


def build_manifest(*, manifest_version, classes_cfg, sources, label_mapping, images,
                   exported, split_rule, exclusions, config_sha256):
    """Ghép manifest. `images`: list dict mức ảnh; `sources`: nội dung SOURCE.json từng nguồn."""
    split_counts = Counter(img["split"] for img in images)
    return {
        "manifest_version": manifest_version,
        "classes_version": classes_cfg["classes_version"],
        "sources": sources,
        "label_mapping": label_mapping,
        "split_rule": split_rule,
        "image_count": len(images),
        "image_count_by_split": {s: split_counts.get(s, 0) for s in ("train", "val", "test")},
        "object_count": count_objects(exported, classes_cfg["classes"]),
        "exclusions": exclusions,
        "config_sha256": config_sha256,
        "images": images,
    }


def label_mapping_rows(classes_cfg, dropped):
    """Bảng ánh xạ cho manifest: nhãn đã map và nhãn bị bỏ cùng số object."""
    rows = []
    for source, src in classes_cfg["sources"].items():
        for orig, code in sorted((src.get("mapping") or {}).items()):
            rows.append({"source": source, "original_label": orig, "sign_type_code": code,
                         "mapping_status": src.get("status")})
    for (source, orig), n in sorted(dropped.items()):
        rows.append({"source": source, "original_label": orig, "sign_type_code": None,
                     "mapping_status": "dropped", "object_count": n})
    return rows
