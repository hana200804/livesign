"""Đo model trên tập test (mặc định test Hà Nội) → ai/results/model-metrics.csv.

Chạy từ gốc repo:
    python ai/scripts/eval_model.py --model data/models/livesign-yolov8n-qcvn12-r001.pt \
        --data data/processed/hanoi-test/data.yaml --test-manifest data/processed/hanoi-test/manifest.json \
        --inference-mode full_640

`--data` là yaml Ultralytics có split `test` và `names` đúng classes.yaml. Ra thêm
ai/results/model-recall-by-size.csv (đúng loại + IoU ≥0,50, nhóm cỡ theo COCO).
Không tune ngưỡng hay chọn checkpoint trên tập này.
"""

import argparse
import csv
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from livesign_ai.boxes import yolo_to_xyxy  # noqa: E402
from livesign_ai.config import REPO, file_sha256, load_classes, load_params, load_yaml, param  # noqa: E402
from livesign_ai.dataset import IMAGE_EXTS, label_path_for, parse_names, parse_yolo_label  # noqa: E402
from livesign_ai.recall import recall_by, size_bucket  # noqa: E402
from livesign_ai.runs import metric_rows  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
RESULTS = REPO / "ai" / "results"


def append_csv(path, rows):
    new = not path.exists()
    with open(path, "a", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        if new:
            w.writeheader()
        w.writerows(rows)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True, type=Path)
    ap.add_argument("--data", required=True, type=Path)
    ap.add_argument("--test-manifest", required=True, type=Path)
    ap.add_argument("--inference-mode", required=True, help="vd. full_640, full_2048, tiled_640")
    ap.add_argument("--imgsz", type=int, default=640)
    args = ap.parse_args()

    from PIL import Image
    from ultralytics import YOLO

    classes_cfg, params = load_classes(), load_params()
    names = parse_names(load_yaml(args.data)["names"])
    if names != classes_cfg["classes"]:
        sys.exit("names trong --data lệch classes.yaml")
    model_version = args.model.stem
    test_hash = file_sha256(args.test_manifest)

    model = YOLO(str(args.model))
    metrics = model.val(data=str(args.data), split="test", imgsz=args.imgsz, plots=False)
    rows = metric_rows(metrics.summary(), model_version=model_version,
                       classes_version=classes_cfg["classes_version"], test_manifest_hash=test_hash,
                       inference_mode=args.inference_mode, classes=classes_cfg["classes"])
    append_csv(RESULTS / "model-metrics.csv", rows)

    # Recall theo cỡ bbox: đối sánh riêng, cùng ngưỡng conf/NMS dùng cho app (model.md).
    data_cfg = load_yaml(args.data)
    root = Path(data_cfg.get("path", args.data.parent))
    root = root if root.is_absolute() else (REPO / root)
    images = []
    for img in sorted(p for p in (root / data_cfg["test"]).rglob("*") if p.suffix.lower() in IMAGE_EXTS):
        w, h = Image.open(img).size
        lab = label_path_for(img)
        gts = [{"box": yolo_to_xyxy(*o.yolo, w, h), "class_id": {v: k for k, v in names.items()}[o.orig_label]}
               for o in (parse_yolo_label(lab.read_text(encoding="utf-8"), names) if lab and lab.exists() else [])]
        res = model.predict(str(img), imgsz=args.imgsz, conf=param(params, "model.confidence_min"),
                            iou=param(params, "model.nms_iou"), verbose=False)[0]
        preds = [{"box": b.tolist(), "class_id": int(c), "score": float(s)}
                 for b, c, s in zip(res.boxes.xyxy, res.boxes.cls, res.boxes.conf)]
        images.append((gts, preds, {}))
    iou_thr = param(params, "evaluation.detection_match_iou")
    by_size = recall_by(images, iou_thr, lambda g, m: (names[g["class_id"]], size_bucket(g["box"])))
    append_csv(RESULTS / "model-recall-by-size.csv", [
        {"date": date.today().isoformat(), "model_version": model_version, "test_manifest_hash": test_hash,
         "inference_mode": args.inference_mode, "sign_type_code": code, "size_bucket": bucket,
         "detected": f, "total": t, "recall": r}
        for (code, bucket), (f, t, r) in by_size.items()])
    print(f"{model_version}: đã ghi {len(rows)} dòng vào {RESULTS / 'model-metrics.csv'}")


if __name__ == "__main__":
    main()
