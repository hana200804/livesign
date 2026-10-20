"""Dòng lần thấy lượt 1 theo docs/contracts/observation.md (schema 1.0, bổ sung 1.1/1.2).

Lượt 1 chỉ ra `seen` hoặc `unknown`. Vị trí/hướng mặt biển để null: `core` quyết.
"""

import math
from datetime import datetime, timezone

from livesign_ai.geo import distance_m, ray_offsets
from livesign_ai.geometry import DEFAULT_FOCAL, box_angle

OUTCOMES = {"seen", "not_seen", "unknown"}
VISIBILITY_METHODS = {"unverified", "independent_model_audited", "human", "automatic_rules"}
SOURCES = {"mapillary", "android_replay"}
FLAGS = ("in_fov", "facing_camera", "sharp", "unoccluded")
COLUMNS = (
    "observation_id", "source", "trip_id", "sequence_id", "creator_id", "captured_at", "captured_at_precision",
    "image_id", "target_sign_id", "source_sign_id", "source_sign_type", "sign_type_code", "camera_lon",
    "camera_lat", "camera_heading_deg", "sign_lon", "sign_lat", "sign_facing_deg", "distance_m", "off_deg",
    "is_pano", "focal", "image_width_px", "image_height_px", "bbox_px", "detector_confidence", "outcome",
    *FLAGS, "visibility_method", "model_version", "evidence_ref", "source_metadata",
)
REQUIRED = ("observation_id", "source", "trip_id", "captured_at", "captured_at_precision", "image_id",
            "outcome", "visibility_method")
MAX_LEN = {"observation_id": 160, "trip_id": 160, "sequence_id": 128, "creator_id": 128, "image_id": 128,
           "target_sign_id": 160, "source_sign_id": 128, "source_sign_type": 160, "model_version": 128,
           "evidence_ref": 512}


def captured_at_utc(ms):
    """captured_at của Mapillary (ms epoch) → RFC3339 UTC, precision instant."""
    dt = datetime.fromtimestamp(ms / 1000, tz=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S.") + f"{dt.microsecond // 1000:03d}Z"


def camera_from_image(img):
    """Trích camera từ object ảnh Graph API. Ưu tiên computed_*, ghi nguồn vào provenance."""
    geom, geom_src = img.get("computed_geometry"), "computed_geometry"
    if not geom:
        geom, geom_src = img.get("geometry"), "geometry"
    heading, head_src = img.get("computed_compass_angle"), "computed_compass_angle"
    if heading is None:
        heading, head_src = img.get("compass_angle"), "compass_angle"
    cam_type = img.get("camera_type") or ""
    is_pano = None if not cam_type else cam_type in ("spherical", "equirectangular")
    params = img.get("camera_parameters") or []
    focal = params[0] if params and not is_pano and params[0] and params[0] > 0 else None
    lon, lat = (geom["coordinates"] if geom else (None, None))
    return {
        "camera_lon": lon, "camera_lat": lat,
        "camera_heading_deg": (heading % 360) if heading is not None else None,
        "is_pano": is_pano, "focal": focal,
        "geometry_source": geom_src if geom else None, "heading_source": head_src if heading is not None else None,
    }


def in_fov(off_deg, w, h, is_pano, focal, keep):
    """Biển nằm trong `keep` × nửa góc ngang (PoC giữ 85%). Pano: luôn trong khung."""
    if off_deg is None or is_pano is None:
        return None
    if is_pano:
        return True
    half = math.degrees(math.atan((w / 2) / ((focal or DEFAULT_FOCAL) * max(w, h))))
    return abs(off_deg) <= keep * half


def feature_candidates(cam, ray_bearing, features, type_values, max_dist_m, max_ray_m):
    """Feature Mapillary cùng loại ánh xạ được, phía trước camera, trong `max_dist_m`, gần tia nhìn."""
    out = []
    for f in features:
        if f.get("object_value") not in type_values:
            continue
        lon, lat = f["geometry"]["coordinates"]
        if distance_m(cam["camera_lon"], cam["camera_lat"], lon, lat) > max_dist_m:
            continue
        along, perp = ray_offsets(cam["camera_lon"], cam["camera_lat"], ray_bearing, lon, lat)
        if along <= 0 or perp > max_ray_m:
            continue
        out.append({"source_sign_id": str(f["id"]), "source_sign_type": f["object_value"],
                    "lon": lon, "lat": lat, "ray_distance_m": round(perp, 2)})
    return sorted(out, key=lambda c: c["ray_distance_m"])


def detection_rows(img, dets, *, w, h, classes, label_candidates, features, model_version, keep,
                   max_dist_m, max_ray_m, inference_meta, data_revision):
    """Mọi detection của một ảnh → list dòng `seen`/`unknown`, thứ tự theo x1 để ID ổn định.

    `dets`: list {class_id, score, box} pixel toàn ảnh, đã NMS.
    """
    cam = camera_from_image(img)
    seq = str(img["sequence"]) if img.get("sequence") else None
    creator = img.get("creator") or {}
    rows = []
    for k, d in enumerate(sorted(dets, key=lambda d: (d["box"][0], d["box"][1], d["class_id"]))):
        code = classes[d["class_id"]]
        cx = (d["box"][0] + d["box"][2]) / 2
        has_cam = cam["camera_lon"] is not None and cam["camera_heading_deg"] is not None and cam["is_pano"] is not None
        off = ((box_angle(cx, w, h, cam["is_pano"], cam["focal"]) + 180) % 360) - 180 if has_cam else None
        cands = []
        if has_cam:
            ray = (cam["camera_heading_deg"] + off) % 360
            cands = feature_candidates(cam, ray, features, set(label_candidates.get(code, [])), max_dist_m, max_ray_m)
        outcome = "seen" if has_cam and creator.get("id") and img.get("captured_at") else "unknown"
        rows.append({
            "observation_id": f"mly:{img['id']}:{model_version}:{k}",
            "source": "mapillary",
            "trip_id": f"mly-seq:{seq}" if seq else f"mly-img:{img['id']}",
            "sequence_id": seq,
            "creator_id": f"mapillary:{creator['id']}" if creator.get("id") else None,
            "captured_at": captured_at_utc(img["captured_at"]),
            "captured_at_precision": "instant",
            "image_id": str(img["id"]),
            "target_sign_id": None, "source_sign_id": None, "source_sign_type": None,
            "sign_type_code": code,
            "camera_lon": cam["camera_lon"], "camera_lat": cam["camera_lat"],
            "camera_heading_deg": cam["camera_heading_deg"],
            "sign_lon": None, "sign_lat": None, "sign_facing_deg": None, "distance_m": None,
            "off_deg": round(off, 3) if off is not None else None,
            "is_pano": cam["is_pano"], "focal": cam["focal"],
            "image_width_px": w, "image_height_px": h,
            "bbox_px": [round(v, 2) for v in d["box"]],
            "detector_confidence": round(d["score"], 5),
            "outcome": outcome,
            "in_fov": in_fov(off, w, h, cam["is_pano"], cam["focal"], keep),
            "facing_camera": None, "sharp": None, "unoccluded": None,
            "visibility_method": "unverified",
            "model_version": model_version,
            "evidence_ref": None,
            "source_metadata": {
                "positioning": {"method": None, "feature_candidates": cands},
                "inference": {**inference_meta, "tile_origin_px": d.get("tile_origin_px", [0, 0]),
                              "full_image_size_px": [w, h], "box_units": "full_image_pixels_xyxy"},
                "provenance": {"data_revision": data_revision, "geometry_source": cam["geometry_source"],
                               "heading_source": cam["heading_source"], "visibility_audit_ref": None},
            },
        })
    return rows


def _num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and v == v and abs(v) != float("inf")


def validate_row(row):
    """Kiểm một dòng theo bảng cột của observation.md. Trả về list lỗi; rỗng là hợp lệ."""
    errs = []
    extra = set(row) - set(COLUMNS)
    if extra:
        errs.append(f"cột lạ: {sorted(extra)}")
    for c in REQUIRED:
        if row.get(c) in (None, ""):
            errs.append(f"thiếu {c}")
    for c, n in MAX_LEN.items():
        v = row.get(c)
        if v is not None and (not isinstance(v, str) or not v or len(v) > n):
            errs.append(f"{c} phải là string 1–{n} ký tự")
    if row.get("source") not in SOURCES:
        errs.append("source sai")
    if row.get("outcome") not in OUTCOMES:
        errs.append("outcome sai")
    if row.get("visibility_method") not in VISIBILITY_METHODS:
        errs.append("visibility_method sai")
    if row.get("captured_at_precision") not in ("instant", "day"):
        errs.append("captured_at_precision sai")
    for c, lo, hi in (("camera_lon", -180, 180), ("camera_lat", -90, 90), ("sign_lon", -180, 180),
                      ("sign_lat", -90, 90), ("detector_confidence", 0, 1)):
        v = row.get(c)
        if v is not None and (not _num(v) or not lo <= v <= hi):
            errs.append(f"{c} ngoài [{lo},{hi}]")
    for c in ("camera_heading_deg", "sign_facing_deg"):
        v = row.get(c)
        if v is not None and (not _num(v) or not 0 <= v < 360):
            errs.append(f"{c} ngoài [0,360)")
    v = row.get("off_deg")
    if v is not None and (not _num(v) or not -180 <= v < 180):
        errs.append("off_deg ngoài [-180,180)")
    v = row.get("distance_m")
    if v is not None and (not _num(v) or v < 0):
        errs.append("distance_m âm")
    v = row.get("focal")
    if v is not None and (not _num(v) or v <= 0):
        errs.append("focal phải > 0")
    for a, b in (("camera_lon", "camera_lat"), ("sign_lon", "sign_lat"), ("image_width_px", "image_height_px")):
        if (row.get(a) is None) != (row.get(b) is None):
            errs.append(f"{a}/{b} phải cùng có hoặc cùng thiếu")
    bbox = row.get("bbox_px")
    if bbox is not None:
        if not (isinstance(bbox, list) and len(bbox) == 4 and all(_num(x) for x in bbox)):
            errs.append("bbox_px phải là 4 số")
        elif not (bbox[0] < bbox[2] and bbox[1] < bbox[3]):
            errs.append("bbox_px phải x1<x2, y1<y2")
        elif row.get("image_width_px") is None:
            errs.append("bbox_px cần kích thước ảnh")
        elif bbox[0] < 0 or bbox[1] < 0 or bbox[2] > row["image_width_px"] or bbox[3] > row["image_height_px"]:
            errs.append("bbox_px ra ngoài ảnh")
    if (bbox is not None or row.get("detector_confidence") is not None) and not row.get("model_version"):
        errs.append("có bbox/confidence thì phải có model_version")
    for f in FLAGS:
        if row.get(f) not in (None, True, False):
            errs.append(f"{f} phải là boolean/null")
    if row.get("outcome") == "not_seen":
        if not row.get("target_sign_id") or not row.get("sign_type_code"):
            errs.append("not_seen cần target_sign_id và sign_type_code")
        if bbox is not None or row.get("detector_confidence") is not None:
            errs.append("not_seen phải có bbox_px/detector_confidence null")
        if not all(row.get(f) is True for f in FLAGS):
            errs.append("not_seen cần cả bốn cờ true")
        if row.get("distance_m") is None or row["distance_m"] > 30:
            errs.append("not_seen cần distance_m ≤ 30")
    return errs
