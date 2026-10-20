"""Tham số train và run manifest. Thuần Python để test được, script mới gọi Ultralytics.

Mọi giá trị train lấy từ config/params.yaml (nhóm `training`), không để thư viện tự chọn.
"""

from livesign_ai.config import param

# Ultralytics mặc định fliplr=0.5: lật ngang làm P.123a (cấm rẽ trái) thành P.123b.
FORCED = {"fliplr": 0.0, "deterministic": True}


def train_args(params, *, where, data, name, project, seed=0, model="yolov8n.pt"):
    if where not in ("local", "colab"):
        raise ValueError("where phải là local hoặc colab")
    args = {
        "model": model,
        "data": data,
        "imgsz": param(params, "training.imgsz"),
        "epochs": param(params, "training.epochs"),
        "batch": param(params, f"training.batch_{where}"),
        "seed": seed,
        "project": project,
        "name": name,
        "exist_ok": False,
    }
    args.update(FORCED)
    return args


def check_args(args):
    """Chặn cấu hình vi phạm quy tắc dataset/hợp đồng trước khi tốn giờ GPU."""
    if args.get("fliplr", 0.5) != 0.0:
        raise ValueError("fliplr phải là 0.0: lật ngang đổi nghĩa biển rẽ trái/phải")
    for key in ("imgsz", "epochs", "batch"):
        if not args.get(key):
            raise ValueError(f"thiếu {key} trong params.yaml")


def run_manifest(*, model_version, args, versions, gpu, dataset_manifest_sha256, args_yaml_sha256,
                 weights_sha256, elapsed_s, classes_version, params_version):
    return {
        "model_version": model_version,
        "classes_version": classes_version,
        "params_version": params_version,
        "dataset_manifest_sha256": dataset_manifest_sha256,
        "train_args": args,
        "train_args_yaml_sha256": args_yaml_sha256,
        "weights_sha256": weights_sha256,
        "versions": versions,
        "gpu": gpu,
        "elapsed_s": round(elapsed_s, 1),
    }


def metric_rows(summary, *, model_version, classes_version, test_manifest_hash, inference_mode, classes):
    """Đổi `DetMetrics.summary()` sang dòng `model-metrics.csv` (evaluation.md).

    Lớp không có object nào trong test vẫn có dòng, số đo để trống: không ghi 0.
    """
    by_code = {r["Class"]: r for r in summary}
    rows = []
    for code in classes.values():
        r = by_code.get(code)
        rows.append({
            "model_version": model_version,
            "classes_version": classes_version,
            "test_manifest_hash": test_manifest_hash,
            "sign_type_code": code,
            "object_count": r["Instances"] if r else 0,
            "map50": r["mAP50"] if r else None,
            "map50_95": r["mAP50-95"] if r else None,
            "recall": r["Box-R"] if r else None,
            "inference_mode": inference_mode,
        })
    return rows
