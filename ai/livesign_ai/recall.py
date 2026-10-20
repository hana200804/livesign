"""Recall theo nhóm (cỡ bbox, pano, ngày/đêm) bằng đối sánh một–một đúng lớp + IoU.

Dùng cho phần báo riêng biển nhỏ của tập test Hà Nội và cho bài thử B3.
"""

from collections import Counter

from livesign_ai.boxes import area, iou

# Ngưỡng diện tích theo COCO (px²): <32² nhỏ, <96² vừa. Đây là lựa chọn ghi rõ, chưa phải ngưỡng nhóm chốt.
SMALL_MAX = 32 * 32
MEDIUM_MAX = 96 * 96


def size_bucket(box):
    a = area(box)
    return "small" if a < SMALL_MAX else "medium" if a < MEDIUM_MAX else "large"


def match_image(gts, preds, iou_thr):
    """Ghép tham lam theo score giảm dần, mỗi GT/pred dùng tối đa một lần.

    `gts`: list dict {box, class_id}; `preds`: list dict {box, class_id, score}.
    Trả về set chỉ số GT đã được phát hiện.
    """
    hit = set()
    for p in sorted(preds, key=lambda d: -d["score"]):
        best, best_iou = None, iou_thr
        for i, g in enumerate(gts):
            if i in hit or g["class_id"] != p["class_id"]:
                continue
            v = iou(g["box"], p["box"])
            if v >= best_iou:
                best, best_iou = i, v
        if best is not None:
            hit.add(best)
    return hit


def recall_by(images, iou_thr, key):
    """`images`: list (gts, preds, meta). `key(gt, meta)` → tên nhóm.

    Trả về {nhóm: (đã phát hiện, tổng, recall hoặc None khi tổng = 0)}.
    """
    found, total = Counter(), Counter()
    for gts, preds, meta in images:
        hit = match_image(gts, preds, iou_thr)
        for i, g in enumerate(gts):
            k = key(g, meta)
            total[k] += 1
            found[k] += i in hit
    return {k: (found[k], total[k], found[k] / total[k] if total[k] else None) for k in sorted(total)}
