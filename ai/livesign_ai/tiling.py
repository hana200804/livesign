"""Chia ô ảnh lớn cho batch nhận diện (ai-pipeline.md, mục Nhận diện hàng loạt).

Ô cạnh `tile` px, chồng `overlap`; ô cuối mỗi trục dồn sát mép ảnh nên khoảng chồng
của cặp ô cuối có thể lớn hơn `overlap`. Danh sách offset được lưu vào run manifest.
"""

import math

from livesign_ai.boxes import clip_box, nms_per_class


def axis_offsets(length, tile, overlap):
    if length <= tile:
        return [0]
    stride = round(tile * (1 - overlap))
    n = math.ceil((length - tile) / stride) + 1
    return sorted({min(i * stride, length - tile) for i in range(n)})


def tile_offsets(w, h, tile, overlap):
    """List (x, y) góc trên trái của từng ô, quét theo hàng."""
    return [(x, y) for y in axis_offsets(h, tile, overlap) for x in axis_offsets(w, tile, overlap)]


def merge_tiles(per_tile, img_w, img_h, nms_iou):
    """`per_tile`: list ((x0, y0), dets theo pixel ô). Cộng offset, clip, NMS theo lớp toàn ảnh.

    Biển nằm trong vùng chồng chỉ còn một detection, không gửi hai observation.
    """
    dets = []
    for (x0, y0), tile_dets in per_tile:
        for d in tile_dets:
            x1, y1, x2, y2 = d["box"]
            box = clip_box([x1 + x0, y1 + y0, x2 + x0, y2 + y0], img_w, img_h)
            dets.append({**d, "box": box, "tile_origin_px": [x0, y0]})
    return nms_per_class(dets, nms_iou)
