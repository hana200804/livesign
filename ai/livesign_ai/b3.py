"""Bài thử giữ/bỏ B3 (ai-pipeline.md, mục Bài thử giữ/bỏ B3).

Mẫu số: cặp tấm–ảnh thuộc 12 lớp, nhãn nhìn được độc lập là `visible`, nguồn ≥2048 px.
Tử số: cặp trong mẫu số có detection đúng loại, IoU ≥ evaluation.detection_match_iou.
Nhãn nhìn được là CSV theo observation.md (mục Nhãn khả năng nhìn được) thêm bbox gán tay.
"""

import random

from livesign_ai.recall import match_image, size_bucket

LABEL_COLUMNS = ("source_sign_id", "image_id", "creator_id", "captured_at", "sequence_id", "sign_type_code",
                 "visibility_label", "labeler", "labeled_at", "reason", "labeler_model_version",
                 "is_pano", "image_long_edge_px", "x1", "y1", "x2", "y2")


def parse_label(row):
    """Một dòng CSV → dict chuẩn. Ô trống thành None; bbox thành list số."""
    def opt(k):
        v = (row.get(k) or "").strip()
        return v or None

    bbox = [float(row[k]) for k in ("x1", "y1", "x2", "y2")] if all(opt(k) for k in ("x1", "y1", "x2", "y2")) else None
    pano = opt("is_pano")
    edge = opt("image_long_edge_px")
    return {
        "source_sign_id": opt("source_sign_id"), "image_id": opt("image_id"), "creator_id": opt("creator_id"),
        "sequence_id": opt("sequence_id"), "sign_type_code": opt("sign_type_code"),
        "visibility_label": opt("visibility_label"), "is_pano": None if pano is None else pano.lower() == "true",
        "image_long_edge_px": int(edge) if edge else None, "bbox": bbox,
    }


def denominator(labels, codes, min_long_edge):
    """Lọc mẫu số; trả về (cặp dùng, Counter lý do loại) để báo cùng kết quả."""
    from collections import Counter

    used, skipped = [], Counter()
    for lab in labels:
        if lab["visibility_label"] != "visible":
            skipped[f"visibility={lab['visibility_label']}"] += 1
        elif lab["sign_type_code"] not in codes:
            skipped["ngoài 12 lớp"] += 1
        elif lab["bbox"] is None:
            skipped["thiếu bbox gán tay"] += 1
        elif lab["image_long_edge_px"] is not None and lab["image_long_edge_px"] < min_long_edge:
            skipped[f"ảnh < {min_long_edge}px"] += 1
        else:
            used.append(lab)
    return used, skipped


def detected_pairs(pairs, preds_by_image, code_to_id, iou_thr):
    """Đánh dấu từng cặp có được phát hiện không. `preds_by_image`: image_id → list {box, class_id, score}."""
    by_image = {}
    for i, p in enumerate(pairs):
        by_image.setdefault(p["image_id"], []).append(i)
    hit = [False] * len(pairs)
    for image_id, idx in by_image.items():
        gts = [{"box": pairs[i]["bbox"], "class_id": code_to_id[pairs[i]["sign_type_code"]]} for i in idx]
        for j in match_image(gts, preds_by_image.get(image_id, []), iou_thr):
            hit[idx[j]] = True
    return hit


def ratio_by(pairs, hit, key):
    out = {}
    for p, h in zip(pairs, hit):
        k = key(p)
        d, t = out.get(k, (0, 0))
        out[k] = (d + h, t + 1)
    return {k: {"detected": d, "total": t, "r": d / t if t else None} for k, (d, t) in sorted(out.items(), key=str)}


def summarize(pairs, hit):
    groups = {
        "all": lambda p: "all",
        "sign_type_code": lambda p: p["sign_type_code"],
        "is_pano": lambda p: p["is_pano"],
        "size_bucket": lambda p: size_bucket(p["bbox"]),
        "creator_id": lambda p: p["creator_id"],
        "sequence_id": lambda p: p["sequence_id"],
    }
    return {
        "pairs": len(pairs),
        "unique_images": len({p["image_id"] for p in pairs}),
        "unique_creators": len({p["creator_id"] for p in pairs if p["creator_id"]}),
        "by": {name: ratio_by(pairs, hit, fn) for name, fn in groups.items()},
    }


def decide(r, keep_min, drop_below):
    """Quyết định theo params: ≥keep giữ, <drop bỏ, khoảng giữa chỉ là phân tích phụ."""
    if r is None:
        return "not_run"
    if r >= keep_min:
        return "keep"
    if r < drop_below:
        return "drop"
    return "secondary_only"


def audit_sample(image_ids, n, seed):
    """`n` image_id khác nhau chọn ngẫu nhiên có seed để người duyệt nhãn nhìn được."""
    ids = sorted(set(image_ids))
    return sorted(random.Random(seed).sample(ids, min(n, len(ids))))
