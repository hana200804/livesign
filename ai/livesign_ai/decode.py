"""Tiền xử lý letterbox và giải mã tensor YOLOv8 detect theo docs/contracts/model.md.

Thuần Python để test được và để làm chuẩn parity với app Android. Script có thể
đưa numpy array vào sau khi `.tolist()`.
"""

import math

from livesign_ai.boxes import clip_box, nms_per_class


def letterbox_params(w, h, s):
    """Bảng "Tiền xử lý xác định được": r, kích thước resize, pad trái/trên."""
    r = min(s / w, s / h)
    new_w, new_h = round(w * r), round(h * r)
    left, top = math.floor((s - new_w) / 2), math.floor((s - new_h) / 2)
    return {"r": r, "new_w": new_w, "new_h": new_h, "left": left, "top": top, "size": s}


def unletterbox(box, lb):
    """Bbox pixel input S×S → bbox pixel ảnh gốc (chưa clip)."""
    x1, y1, x2, y2 = box
    r = lb["r"]
    return [(x1 - lb["left"]) / r, (y1 - lb["top"]) / r, (x2 - lb["left"]) / r, (y2 - lb["top"]) / r]


def to_anchor_rows(output, layout):
    """Output một ảnh (đã bỏ batch) → list N hàng, mỗi hàng 4 + num_classes giá trị.

    `BCN`: [16][N] như YOLOv8 TFLite thường gặp; `BNC`: [N][16]. Không đoán trục từ dữ liệu.
    """
    if layout == "BNC":
        return [list(row) for row in output]
    if layout == "BCN":
        return [list(col) for col in zip(*output)]
    raise ValueError(f"layout không hỗ trợ: {layout}")


def dequantize(values, scale, zero_point):
    if scale is None:
        return values
    return [(q - zero_point) * scale for q in values]


def decode(output, *, layout, box_units, lb, img_w, img_h, conf_min, nms_iou, scale=None, zero_point=None):
    """Pseudo-code "Tensor đầu ra và giải mã" của model.md.

    Trả về list dict {class_id, score, box} với box pixel toàn ảnh, đã NMS theo lớp.
    """
    s = lb["size"]
    dets = []
    for row in to_anchor_rows(output, layout):
        row = dequantize(row, scale, zero_point)
        scores = row[4:]
        cid = max(range(len(scores)), key=scores.__getitem__)
        score = scores[cid]
        if score < conf_min:
            continue
        cx, cy, w, h = row[:4]
        if box_units == "normalized_xywh":
            cx, cy, w, h = cx * s, cy * s, w * s, h * s
        elif box_units != "input_pixels_xywh":
            raise ValueError(f"box_units không hỗ trợ: {box_units}")
        box = clip_box(unletterbox([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], lb), img_w, img_h)
        if box[2] <= box[0] or box[3] <= box[1]:
            continue
        dets.append({"class_id": cid, "score": score, "box": box})
    return nms_per_class(dets, nms_iou)


def labels_txt(classes_cfg):
    """12 dòng mã QCVN theo class_id 0–11, không tiêu đề, không BOM, xuống dòng LF."""
    classes = classes_cfg["classes"]
    if sorted(classes) != list(range(len(classes))):
        raise ValueError("class_id phải liên tục từ 0")
    return "".join(f"{classes[i]}\n" for i in range(len(classes)))


def model_manifest(*, model_version, classes_version, params_version, config_sha256, artifact_sha256,
                   exporter_version, input_detail, output_detail, size, box_units, layout, conf_min, nms_iou):
    """Mẫu manifest ở model.md. `*_detail`: shape/dtype/quantization đọc từ interpreter thật."""
    def tensor(d, extra):
        q = d.get("quantization") or (None, None)
        scale, zp = (q[0] or None), (q[1] if q[0] else None)
        return {"shape": list(d["shape"]), "dtype": d["dtype"], "scale": scale, "zero_point": zp, **extra}

    return {
        "model_version": model_version,
        "classes_version": classes_version,
        "params_version": params_version,
        "config_sha256": config_sha256,
        "artifact_sha256": artifact_sha256,
        "exporter_version": exporter_version,
        "status": "exported",
        "input": tensor(input_detail, {"layout": "NHWC", "color": "RGB"}),
        "output": tensor(output_detail, {"layout": layout, "box_units": box_units}),
        "letterbox": {"width": size, "height": size, "center": True, "padding_rgb": [114, 114, 114],
                      "auto_stride_pad": False},
        "nms": {"location": "client", "class_agnostic": False, "confidence_min": conf_min, "iou": nms_iou},
    }
