"""Nhận diện hàng loạt một trip Mapillary → JSONL lần thấy lượt 1 (seen/unknown).

Chạy từ gốc repo, sau fetch_mapillary.py:
    python ai/scripts/batch_infer.py --trip data/mapillary/<sequence_id> \
        --weights data/models/livesign-yolov8n-qcvn12-r001.pt --mode tiled

`--mode full`: letterbox toàn ảnh với imgsz = cạnh dài ảnh (tối đa 2048, ghi cỡ input thật).
`--mode tiled`: ô training.tile_size_px, chồng tile_overlap_ratio, NMS lại trên toàn ảnh.
So hai cách bằng eval trên cùng tập phát triển có nhãn, không dùng test Hà Nội.

Ra data/observations/<trip_id>.jsonl (một trip một file) và <trip_id>.run.json (cấu hình,
offset ô, thời gian p50/p95). Dòng không qua validate_row thì dừng, không ghi file hỏng.
"""

import argparse
import json
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from livesign_ai.boxes import nms_per_class  # noqa: E402
from livesign_ai.config import REPO, file_sha256, load_classes, load_params, param  # noqa: E402
from livesign_ai.observation import detection_rows, validate_row  # noqa: E402
from livesign_ai.tiling import merge_tiles, tile_offsets  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")


def read_jsonl(path):
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def to_dets(result):
    b = result.boxes
    return [{"box": box.tolist(), "class_id": int(c), "score": float(s)} for box, c, s in zip(b.xyxy, b.cls, b.conf)]


def percentile(values, q):
    if not values:
        return None
    s = sorted(values)
    return s[min(len(s) - 1, round(q * (len(s) - 1)))]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--trip", required=True, type=Path)
    ap.add_argument("--weights", required=True, type=Path)
    ap.add_argument("--mode", choices=["full", "tiled"], required=True)
    ap.add_argument("--data-revision", default=None, help="vd. hash manifest corpus khi đã khóa")
    args = ap.parse_args()

    from PIL import Image, ImageOps
    from ultralytics import YOLO

    classes_cfg, params = load_classes(), load_params()
    classes = classes_cfg["classes"]
    conf, iou_thr = param(params, "model.confidence_min"), param(params, "model.nms_iou")
    tile, overlap = param(params, "batch_inference.tile_size_px"), param(params, "batch_inference.tile_overlap_ratio")
    long_edge = param(params, "batch_inference.image_long_edge_px")
    candidates = {c: d.get("mapillary_label_candidates", []) for c, d in classes_cfg["definitions"].items()}
    model_version = args.weights.stem
    model = YOLO(str(args.weights))

    images = read_jsonl(args.trip / "images.jsonl")
    features = read_jsonl(args.trip / "features.jsonl")
    rows, times, sizes, offsets_used = [], [], {}, {}
    for im in images:
        path = args.trip / "images" / f"{im['id']}.jpg"
        if not path.exists():
            continue
        img = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
        w, h = img.size
        sizes[(w, h)] = sizes.get((w, h), 0) + 1
        t0 = time.perf_counter()
        if args.mode == "full":
            imgsz = min(max(w, h), long_edge)
            res = model.predict(img, imgsz=imgsz, conf=conf, iou=iou_thr, verbose=False)[0]
            dets = nms_per_class(to_dets(res), iou_thr)
            # Ultralytics làm tròn imgsz theo stride 32; ghi cỡ thật predictor đã dùng.
            real = getattr(model.predictor, "imgsz", imgsz)
            meta = {"input_size_px": max(real) if isinstance(real, (list, tuple)) else real}
        else:
            offs = tile_offsets(w, h, tile, overlap)
            offsets_used[f"{w}x{h}"] = offs
            per_tile = []
            for x0, y0 in offs:
                crop = img.crop((x0, y0, min(x0 + tile, w), min(y0 + tile, h)))
                per_tile.append(((x0, y0), to_dets(model.predict(crop, imgsz=tile, conf=conf, iou=iou_thr,
                                                                 verbose=False)[0])))
            dets = merge_tiles(per_tile, w, h, iou_thr)
            meta = {"input_size_px": tile}
        times.append((time.perf_counter() - t0) * 1000)
        meta.update({"model_weights_sha256": None, "confidence_min": conf, "nms_iou": iou_thr, "mode": args.mode})
        rows += detection_rows(im, dets, w=w, h=h, classes=classes, label_candidates=candidates, features=features,
                               model_version=model_version, keep=param(params, "geometry.perspective_frame_keep"),
                               max_dist_m=param(params, "geometry.visibility_max_distance_m"),
                               max_ray_m=param(params, "positioning.feature_link_max_ray_distance_m"),
                               inference_meta=meta, data_revision=args.data_revision)

    weights_sha = file_sha256(args.weights)
    for r in rows:
        r["source_metadata"]["inference"]["model_weights_sha256"] = weights_sha
    bad = [(r["observation_id"], e) for r in rows for e in validate_row(r)]
    if bad:
        sys.exit(f"{len(bad)} lỗi hợp đồng, ví dụ: {bad[:3]}")

    trip_ids = {r["trip_id"] for r in rows} or {f"mly-seq:{args.trip.name}"}
    if len(trip_ids) > 1:
        sys.exit(f"một lô chỉ một trip, gặp {sorted(trip_ids)}")
    trip_id = trip_ids.pop()
    out_dir = REPO / "data" / "observations"
    out_dir.mkdir(parents=True, exist_ok=True)
    name = trip_id.replace(":", "_")
    with open(out_dir / f"{name}.jsonl", "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    (out_dir / f"{name}.run.json").write_text(json.dumps({
        "trip_id": trip_id, "schema_version": "1.0", "classes_version": classes_cfg["classes_version"],
        "params_version": params["params_version"], "model_version": model_version, "model_weights_sha256": weights_sha,
        "mode": args.mode, "tile_size_px": tile if args.mode == "tiled" else None,
        "tile_overlap_ratio": overlap if args.mode == "tiled" else None, "tile_offsets": offsets_used,
        "image_sizes": {f"{w}x{h}": n for (w, h), n in sizes.items()}, "images": len(times), "rows": len(rows),
        "seen": sum(r["outcome"] == "seen" for r in rows),
        "latency_ms": {"p50": percentile(times, 0.5), "p95": percentile(times, 0.95),
                       "mean": statistics.fmean(times) if times else None},
    }, indent=1), encoding="utf-8")
    print(f"{trip_id}: {len(times)} ảnh, {len(rows)} dòng → {out_dir / (name + '.jsonl')}")


if __name__ == "__main__":
    main()
