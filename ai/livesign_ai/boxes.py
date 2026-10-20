"""Phép tính bbox thuần Python, dùng chung cho dataset, batch nhận diện và bài thử B3.

Quy ước: bbox pixel `[x1, y1, x2, y2]`, gốc góc trên trái, x sang phải, y xuống
(observation.md). YOLO dùng `(cx, cy, w, h)` chuẩn hóa về [0, 1].
"""


def yolo_to_xyxy(cx, cy, w, h, img_w, img_h):
    return [(cx - w / 2) * img_w, (cy - h / 2) * img_h, (cx + w / 2) * img_w, (cy + h / 2) * img_h]


def xyxy_to_yolo(box, img_w, img_h):
    x1, y1, x2, y2 = box
    return ((x1 + x2) / 2 / img_w, (y1 + y2) / 2 / img_h, (x2 - x1) / img_w, (y2 - y1) / img_h)


def clip_box(box, img_w, img_h):
    x1, y1, x2, y2 = box
    return [min(max(x1, 0), img_w), min(max(y1, 0), img_h), min(max(x2, 0), img_w), min(max(y2, 0), img_h)]


def area(box):
    return max(0.0, box[2] - box[0]) * max(0.0, box[3] - box[1])


def iou(a, b):
    inter = area([max(a[0], b[0]), max(a[1], b[1]), min(a[2], b[2]), min(a[3], b[3])])
    union = area(a) + area(b) - inter
    return inter / union if union > 0 else 0.0


def nms_per_class(dets, iou_thr):
    """NMS theo lớp. `dets`: list dict có `box`, `score`, `class_id`.

    Hai lớp khác nhau chung cột không triệt nhau (model.md). Thứ tự ra theo score
    giảm dần, hòa thì giữ thứ tự vào để kết quả xác định.
    """
    order = sorted(range(len(dets)), key=lambda i: -dets[i]["score"])
    kept = []
    for i in order:
        d = dets[i]
        if all(k["class_id"] != d["class_id"] or iou(k["box"], d["box"]) <= iou_thr for k in kept):
            kept.append(d)
    return kept
