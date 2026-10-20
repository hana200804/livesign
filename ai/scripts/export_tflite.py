"""Xuất .pt sang TFLite và tạo gói bàn giao Android theo docs/contracts/model.md.

Chạy từ gốc repo:
    python ai/scripts/export_tflite.py --weights data/models/livesign-yolov8n-qcvn12-r001.pt \
        --rev r001 --imgsz 640 --precision fp16 --parity-dir data/parity-images

Ra data/models/<model_version>/: <model_version>.tflite, labels.txt, model-manifest.json,
parity-cases.jsonl. Không commit các file này; đưa lên Drive chung.

Lần đầu Ultralytics tự cài gói export TFLite (tensorflow/onnx2tf, vài trăm MB).
Layout và đơn vị bbox ghi theo tensor đọc thật; script dừng nếu shape không phải
[1, 4+12, N] hoặc [1, N, 4+12] (graph nhúng NMS không thuộc hợp đồng hiện tại).
"""

import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from livesign_ai.config import CLASSES_YAML, PARAMS_YAML, REPO, file_sha256, load_classes, load_params, param  # noqa: E402
from livesign_ai.decode import decode, labels_txt, letterbox_params, model_manifest  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")


def load_interpreter(path):
    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        from tensorflow.lite.python.interpreter import Interpreter
    it = Interpreter(model_path=str(path))
    it.allocate_tensors()
    return it


def detail(d):
    return {"shape": [int(x) for x in d["shape"]], "dtype": d["dtype"].__name__,
            "quantization": tuple(float(x) if i == 0 else int(x) for i, x in enumerate(d["quantization"]))}


def preprocess(img, lb, in_detail):
    """Letterbox đúng bảng model.md: bilinear, pad 114, RGB, /255; lượng tử nếu input int."""
    import numpy as np
    from PIL import Image

    canvas = Image.new("RGB", (lb["size"], lb["size"]), (114, 114, 114))
    canvas.paste(img.resize((lb["new_w"], lb["new_h"]), Image.BILINEAR), (lb["left"], lb["top"]))
    x = np.asarray(canvas, dtype=np.float32)[None] / 255.0
    scale, zp = in_detail["quantization"]
    if scale:
        x = np.clip(np.round(x / scale) + zp, np.iinfo(in_detail["dtype"]).min, np.iinfo(in_detail["dtype"]).max)
    return x.astype(in_detail["dtype"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--weights", required=True, type=Path)
    ap.add_argument("--rev", required=True)
    ap.add_argument("--imgsz", type=int, choices=[416, 640], default=640)
    ap.add_argument("--precision", choices=["fp16", "fp32", "int8"], default="fp16")
    ap.add_argument("--calib-data", help="yaml dataset train/val cho int8; không dùng test Hà Nội")
    ap.add_argument("--parity-dir", type=Path, help="ảnh kiểm parity có quyền dùng")
    args = ap.parse_args()
    if args.precision == "int8" and not args.calib_data:
        sys.exit("int8 cần --calib-data từ train/val đã đóng")

    import ultralytics
    from PIL import Image, ImageOps
    from ultralytics import YOLO

    classes_cfg, params = load_classes(), load_params()
    model_version = f"livesign-yolov8n-qcvn12-{args.rev}-i{args.imgsz}-{args.precision}"
    out_dir = REPO / "data" / "models" / model_version
    if out_dir.exists():
        sys.exit(f"{out_dir} đã có; tăng revision, không ghi đè bytes dưới cùng tên version")

    kw = {"format": "tflite", "imgsz": args.imgsz, "nms": False}
    if args.precision == "fp16":
        kw["half"] = True
    if args.precision == "int8":
        kw.update(int8=True, data=args.calib_data)
    saved = Path(YOLO(str(args.weights)).export(**kw))
    out_dir.mkdir(parents=True)
    tflite = out_dir / f"{model_version}.tflite"
    shutil.copy2(saved, tflite)

    it = load_interpreter(tflite)
    in_d, out_d = detail(it.get_input_details()[0]), detail(it.get_output_details()[0])
    nc = len(classes_cfg["classes"])
    shape = out_d["shape"]
    if len(shape) != 3 or shape[0] != 1 or 4 + nc not in shape[1:]:
        sys.exit(f"output {shape} không phải [1,{4 + nc},N] hoặc [1,N,{4 + nc}]")
    layout = "BCN" if shape[1] == 4 + nc else "BNC"

    # Đơn vị bbox: chạy một ảnh xám, xem tọa độ có nằm trong [0, 1.5] không. Ghi rõ vào manifest.
    import numpy as np

    it.set_tensor(it.get_input_details()[0]["index"], np.full(in_d["shape"], 0.5, dtype=in_d["dtype"]))
    it.invoke()
    raw = it.get_tensor(it.get_output_details()[0]["index"])[0]
    coords = raw[:4] if layout == "BCN" else raw[:, :4].T
    box_units = "normalized_xywh" if float(np.abs(coords).max()) <= 1.5 else "input_pixels_xywh"

    (out_dir / "labels.txt").write_bytes(labels_txt(classes_cfg).encode("utf-8"))
    conf_min, nms_iou = param(params, "model.confidence_min"), param(params, "model.nms_iou")
    manifest = model_manifest(
        model_version=model_version, classes_version=classes_cfg["classes_version"],
        params_version=params["params_version"],
        config_sha256=hashlib.sha256(CLASSES_YAML.read_bytes() + PARAMS_YAML.read_bytes()).hexdigest(),
        artifact_sha256=file_sha256(tflite), exporter_version=f"ultralytics {ultralytics.__version__}",
        input_detail=in_d, output_detail=out_d, size=args.imgsz, box_units=box_units, layout=layout,
        conf_min=conf_min, nms_iou=nms_iou)
    manifest["source_weights_sha256"] = file_sha256(args.weights)
    (out_dir / "model-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    n = 0
    if args.parity_dir:
        with open(out_dir / "parity-cases.jsonl", "w", encoding="utf-8") as f:
            for p in sorted(args.parity_dir.iterdir()):
                if p.suffix.lower() not in {".jpg", ".jpeg", ".png"}:
                    continue
                img = ImageOps.exif_transpose(Image.open(p)).convert("RGB")
                lb = letterbox_params(img.width, img.height, args.imgsz)
                it.set_tensor(it.get_input_details()[0]["index"], preprocess(img, lb, it.get_input_details()[0]))
                it.invoke()
                out = it.get_tensor(it.get_output_details()[0]["index"])[0].tolist()
                scale, zp = out_d["quantization"]
                dets = decode(out, layout=layout, box_units=box_units, lb=lb, img_w=img.width, img_h=img.height,
                              conf_min=conf_min, nms_iou=nms_iou, scale=scale or None, zero_point=zp if scale else None)
                f.write(json.dumps({
                    "image": p.name, "image_sha256": file_sha256(p), "width": img.width, "height": img.height,
                    "letterbox": lb, "model_version": model_version,
                    "expected": [{"sign_type_code": classes_cfg["classes"][d["class_id"]], "class_id": d["class_id"],
                                  "confidence": round(d["score"], 5), "bbox_px": [round(v, 2) for v in d["box"]]}
                                 for d in dets],
                    "tolerance": {"bbox_px": None, "confidence": None},  # nhóm duyệt dung sai
                }, ensure_ascii=False) + "\n")
                n += 1
    print(f"{model_version}: {layout}, {box_units}, {n} ca parity → {out_dir}")


if __name__ == "__main__":
    main()
