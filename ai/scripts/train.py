"""Train YOLOv8n 12 lớp theo params.yaml và ghi run manifest.

Chạy từ gốc repo, sau khi build_dataset.py đã tạo data/processed/yolo:
    python ai/scripts/train.py --where local --rev r001 --dataset-manifest data/processed/manifest-r001.json
    python ai/scripts/train.py --where colab --rev r001 --dataset-manifest ...   # trên Colab

Ra data/runs/<rev>/ (Ultralytics), data/runs/<rev>/run-manifest.json và
data/models/livesign-yolov8n-qcvn12-<rev>.pt. Checkpoint không commit.
"""

import argparse
import json
import os
import platform
import shutil
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from livesign_ai.config import DATASET_YAML, REPO, file_sha256, load_classes, load_params  # noqa: E402
from livesign_ai.runs import check_args, run_manifest, train_args  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--where", choices=["local", "colab"], required=True)
    ap.add_argument("--rev", required=True, help="vd. r001; trùng thư mục cũ thì dừng, không ghi đè")
    ap.add_argument("--dataset-manifest", required=True, type=Path)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--batch", type=int, help="ghi đè batch khi thiếu VRAM; được ghi vào manifest")
    ap.add_argument("--epochs", type=int, help="chỉ để chạy thử script; run thật dùng params.yaml")
    # Worker đa tiến trình của dataloader bị segfault trên Windows (torch 2.14, đo 20/10/2026).
    ap.add_argument("--workers", type=int, default=0 if os.name == "nt" else 8)
    args = ap.parse_args()

    import torch
    import ultralytics
    from ultralytics import YOLO

    params, classes = load_params(), load_classes()
    project = REPO / "data" / "runs"
    targs = train_args(params, where=args.where, data=str(DATASET_YAML), name=args.rev,
                       project=str(project), seed=args.seed)
    if args.batch:
        targs["batch"] = args.batch
    if args.epochs:
        targs["epochs"] = args.epochs
    targs["workers"] = args.workers
    check_args(targs)

    t0 = time.time()
    model = YOLO(targs.pop("model"))
    model.train(**targs)
    elapsed = time.time() - t0

    run_dir = project / args.rev
    best = run_dir / "weights" / "best.pt"
    model_version = f"livesign-yolov8n-qcvn12-{args.rev}"
    dst = REPO / "data" / "models" / f"{model_version}.pt"
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(best, dst)

    manifest = run_manifest(
        model_version=model_version, args=targs,
        versions={"ultralytics": ultralytics.__version__, "torch": torch.__version__, "python": platform.python_version()},
        gpu=torch.cuda.get_device_name(0) if torch.cuda.is_available() else None,
        dataset_manifest_sha256=file_sha256(args.dataset_manifest),
        args_yaml_sha256=file_sha256(run_dir / "args.yaml"),
        weights_sha256=file_sha256(dst), elapsed_s=elapsed,
        classes_version=classes["classes_version"], params_version=params["params_version"])
    (run_dir / "run-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{model_version}: {elapsed / 60:.1f} phút → {dst}")


if __name__ == "__main__":
    main()
