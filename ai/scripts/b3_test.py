"""Bài thử giữ/bỏ B3: tỷ lệ phát hiện trên cặp nhìn rõ đã gán độc lập.

Chạy từ gốc repo:
    # 1. Chọn 60 ảnh cho người duyệt nhãn nhìn được (khi nhãn do model phụ gán)
    python ai/scripts/b3_test.py audit --labels data/b3/visibility-labels.csv
    # 2. Chấm detector, đầu vào là JSONL lượt 1 của batch_infer.py trên cùng ảnh
    python ai/scripts/b3_test.py score --labels data/b3/visibility-labels.csv \
        --observations data/observations/mly-seq_<id>.jsonl [--observations ...]

CSV nhãn cột: source_sign_id, image_id, creator_id, captured_at, sequence_id, sign_type_code,
visibility_label (visible|not_visible|unknown), labeler, labeled_at, reason, labeler_model_version,
is_pano, image_long_edge_px, x1, y1, x2, y2 (bbox gán tay, pixel ảnh gốc).

`score` ghi thêm một dòng vào ai/results/b3-test.csv và in quyết định theo params.
Không đổi negative_evidence_enabled tự động: nhóm đổi trong PR có lý do.
"""

import argparse
import csv
import hashlib
import json
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from livesign_ai.b3 import audit_sample, decide, denominator, detected_pairs, parse_label, summarize  # noqa: E402
from livesign_ai.config import REPO, code_to_id, load_classes, load_params, param  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")


def read_labels(path):
    with open(path, encoding="utf-8-sig") as f:
        return [parse_label(r) for r in csv.DictReader(f)]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["audit", "score"])
    ap.add_argument("--labels", required=True, type=Path)
    ap.add_argument("--observations", action="append", type=Path, default=[])
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    params, classes_cfg = load_params(), load_classes()
    labels = read_labels(args.labels)
    if args.cmd == "audit":
        n = param(params, "evaluation.human_audit_images")
        for image_id in audit_sample([l["image_id"] for l in labels], n, args.seed):
            print(image_id)
        return

    codes = set(classes_cfg["classes"].values())
    pairs, skipped = denominator(labels, codes, param(params, "batch_inference.image_long_edge_px"))
    preds, models = {}, set()
    for obs in args.observations:
        for line in obs.read_text(encoding="utf-8").splitlines():
            r = json.loads(line)
            if r.get("bbox_px") and r.get("sign_type_code") in codes:
                preds.setdefault(r["image_id"], []).append({"box": r["bbox_px"], "score": r["detector_confidence"],
                                                            "class_id": code_to_id(classes_cfg)[r["sign_type_code"]]})
                models.add(r["model_version"])
    if len(models) > 1:
        sys.exit(f"nhiều model trong cùng lần chấm: {sorted(models)}")
    missing = {p["image_id"] for p in pairs} - set(preds)
    iou_thr = param(params, "evaluation.detection_match_iou")
    hit = detected_pairs(pairs, preds, code_to_id(classes_cfg), iou_thr)
    summary = summarize(pairs, hit)
    r = summary["by"]["all"].get("all", {}).get("r")
    decision = decide(r, param(params, "evaluation.retain_negative_evidence_min"),
                      param(params, "evaluation.drop_negative_evidence_below"))

    row = {"date": date.today().isoformat(), "model_version": next(iter(models), None),
           "labels_sha256": hashlib.sha256(args.labels.read_bytes()).hexdigest(),
           "pairs": summary["pairs"], "detected": summary["by"]["all"].get("all", {}).get("detected", 0),
           "r": r, "decision": decision, "result_status": "measured" if r is not None else "not_run",
           "unique_images": summary["unique_images"], "unique_creators": summary["unique_creators"],
           "images_without_observations": len(missing), "iou": iou_thr,
           "skipped": json.dumps(dict(skipped), ensure_ascii=False),
           "by_class": json.dumps(summary["by"]["sign_type_code"], ensure_ascii=False)}
    out = REPO / "ai" / "results" / "b3-test.csv"
    new = not out.exists()
    with open(out, "a", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(row))
        if new:
            w.writeheader()
        w.writerow(row)
    (REPO / "data" / "b3").mkdir(parents=True, exist_ok=True)
    (REPO / "data" / "b3" / "b3-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1),
                                                         encoding="utf-8")
    print(f"r = {r} trên {summary['pairs']} cặp / {summary['unique_images']} ảnh → {decision}")
    if missing:
        print(f"cảnh báo: {len(missing)} ảnh mẫu số không có dòng lượt 1 (tính là không phát hiện)")


if __name__ == "__main__":
    main()
