"""Tải ảnh + metadata một sequence Mapillary và feature biển quanh nó, làm đầu vào batch_infer.py.

Chạy từ gốc repo, token đọc từ MAPILLARY_TOKEN (biến môi trường hoặc .env):
    python ai/scripts/fetch_mapillary.py --sequence <sequence_id> [--sequence ...]

Ra data/mapillary/<sequence_id>/:
- images.jsonl: object ảnh Graph API (id, sequence, creator, captured_at, computed_*, camera_*, ...)
- images/<image_id>.jpg: thumb_2048 (cạnh dài 2048 px khi nguồn có; không upscale)
- features.jsonl: map feature biển có nhãn thuộc mapillary_label_candidates của classes.yaml
- fetch-manifest.json: thời điểm tải, số ảnh, sha256 từng ảnh

Đây là công cụ cho phần AI chạy thử. Corpus tuyến demo ba năm có manifest khóa là
việc của dòng "Dữ liệu" trong MVP; khi có thì batch_infer.py đọc cùng bố cục này.
"""

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from livesign_ai.config import REPO, file_sha256, load_classes  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
API = "https://graph.mapillary.com"
IMG_FIELDS = ("id,sequence,captured_at,creator,computed_geometry,geometry,computed_compass_angle,compass_angle,"
              "camera_type,camera_parameters,width,height,thumb_2048_url")
FEATURE_FIELDS = "id,object_value,geometry,first_seen_at,last_seen_at"


def load_token():
    env = REPO / ".env"
    if "MAPILLARY_TOKEN" not in os.environ and env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.startswith("MAPILLARY_TOKEN="):
                os.environ["MAPILLARY_TOKEN"] = line.split("=", 1)[1].strip()
    token = os.environ.get("MAPILLARY_TOKEN")
    if not token:
        sys.exit("thiếu MAPILLARY_TOKEN")
    return token


def get_json(url, token, tries=3):
    req = urllib.request.Request(url, headers={"Authorization": f"OAuth {token}"})
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except OSError:
            if i == tries - 1:
                raise
            time.sleep(2 ** i)


def bbox_around(images, pad_deg=0.0004):
    lons = [im["computed_geometry"]["coordinates"][0] for im in images if im.get("computed_geometry")]
    lats = [im["computed_geometry"]["coordinates"][1] for im in images if im.get("computed_geometry")]
    return [min(lons) - pad_deg, min(lats) - pad_deg, max(lons) + pad_deg, max(lats) + pad_deg]


def fetch_sequence(seq, token, object_values):
    out = REPO / "data" / "mapillary" / seq
    (out / "images").mkdir(parents=True, exist_ok=True)
    q = urllib.parse.urlencode({"sequence_ids": seq, "fields": IMG_FIELDS, "limit": 2000})
    images = sorted(get_json(f"{API}/images?{q}", token)["data"], key=lambda im: im["captured_at"])
    hashes = {}
    for im in images:
        dst = out / "images" / f"{im['id']}.jpg"
        if not dst.exists() and im.get("thumb_2048_url"):
            urllib.request.urlretrieve(im.pop("thumb_2048_url"), dst)
        im.pop("thumb_2048_url", None)  # URL có chữ ký tạm thời, không lưu
        if dst.exists():
            hashes[str(im["id"])] = file_sha256(dst)
    with open(out / "images.jsonl", "w", encoding="utf-8") as f:
        for im in images:
            f.write(json.dumps(im, ensure_ascii=False) + "\n")

    features = []
    if images:
        w, s, e, n = bbox_around(images)
        # Graph API giới hạn diện tích bbox; tuyến dài thì cắt nhỏ.
        q = urllib.parse.urlencode({"bbox": f"{w},{s},{e},{n}", "fields": FEATURE_FIELDS,
                                    "object_values": ",".join(sorted(object_values)), "limit": 2000})
        features = get_json(f"{API}/map_features?{q}", token).get("data", [])
    with open(out / "features.jsonl", "w", encoding="utf-8") as f:
        for ft in features:
            f.write(json.dumps(ft, ensure_ascii=False) + "\n")
    (out / "fetch-manifest.json").write_text(json.dumps({
        "sequence_id": seq, "retrieved_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "image_count": len(images), "image_files": len(hashes), "feature_count": len(features),
        "image_sha256": hashes}, indent=1), encoding="utf-8")
    print(f"{seq}: {len(images)} ảnh, {len(features)} feature → {out}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sequence", action="append", required=True)
    args = ap.parse_args()
    token = load_token()
    values = {v for d in load_classes()["definitions"].values() for v in d.get("mapillary_label_candidates", [])}
    for seq in args.sequence:
        fetch_sequence(seq, token, values)


if __name__ == "__main__":
    main()
