"""Lượt 2: kiểm nhìn được biển đã có trong snapshot và tạo `not_seen` (observation.md, Thay đổi 1.2).

Chỉ ra `not_seen` khi đủ mọi điều kiện của hợp đồng: biển cũ ≤30 m, bốn cờ đều true,
camera đủ (heading gốc, focal thật, không dùng fallback), không phát hiện biển cũ trong
ảnh, và `negative_evidence_enabled` = true. Mọi trường hợp khác là `unknown`.
"""

from livesign_ai.geo import bearing_deg, distance_m
from livesign_ai.geometry import angle_diff
from livesign_ai.observation import camera_from_image, captured_at_utc, in_fov

SKIP_STATUSES = {"removed", "replaced"}


def check_pair(img, sign, *, w, h, keep, max_dist_m, max_facing_diff_deg, sharp=None, unoccluded=None):
    """Bốn cờ cho một cặp ảnh–biển; trả về None nếu biển ngoài bán kính kiểm."""
    cam = camera_from_image(img)
    if cam["camera_lon"] is None or cam["camera_heading_deg"] is None or cam["is_pano"] is None:
        return None
    dist = distance_m(cam["camera_lon"], cam["camera_lat"], sign["sign_lon"], sign["sign_lat"])
    if dist > max_dist_m:
        return None
    to_sign = bearing_deg(cam["camera_lon"], cam["camera_lat"], sign["sign_lon"], sign["sign_lat"])
    off = ((to_sign - cam["camera_heading_deg"] + 180) % 360) - 180
    facing = sign.get("sign_facing_deg")
    facing_ok = None if facing is None else angle_diff(facing, (to_sign + 180) % 360) <= max_facing_diff_deg
    return {
        "cam": cam, "distance_m": dist, "off_deg": off,
        "in_fov": in_fov(off, w, h, cam["is_pano"], cam["focal"], keep),
        "facing_camera": facing_ok, "sharp": sharp, "unoccluded": unoccluded,
    }


def decide_outcome(check, *, detected_same_type, negative_enabled, max_dist_m):
    flags = (check["in_fov"], check["facing_camera"], check["sharp"], check["unoccluded"])
    # focal None = sẽ dùng fallback 0,8: không đủ cơ sở cho not_seen (observation.md).
    cam_ok = check["cam"]["is_pano"] or check["cam"]["focal"] is not None
    if (negative_enabled is True and all(f is True for f in flags) and check["distance_m"] <= max_dist_m
            and cam_ok and check["cam"]["heading_source"] == "computed_compass_angle" and not detected_same_type):
        return "not_seen"
    return "unknown"


def pass2_row(img, sign, check, outcome, *, w, h, snapshot_run_id, data_revision):
    creator = img.get("creator") or {}
    seq = str(img["sequence"]) if img.get("sequence") else None
    cam = check["cam"]
    return {
        "observation_id": f"mly2:{img['id']}:{sign['sign_id']}:{snapshot_run_id}",
        "source": "mapillary",
        "trip_id": f"mly-seq:{seq}" if seq else f"mly-img:{img['id']}",
        "sequence_id": seq,
        "creator_id": f"mapillary:{creator['id']}" if creator.get("id") else None,
        "captured_at": captured_at_utc(img["captured_at"]),
        "captured_at_precision": "instant",
        "image_id": str(img["id"]),
        "target_sign_id": sign["sign_id"], "source_sign_id": None, "source_sign_type": None,
        "sign_type_code": sign["sign_type_code"],
        "camera_lon": cam["camera_lon"], "camera_lat": cam["camera_lat"],
        "camera_heading_deg": cam["camera_heading_deg"],
        "sign_lon": sign["sign_lon"], "sign_lat": sign["sign_lat"], "sign_facing_deg": sign.get("sign_facing_deg"),
        "distance_m": round(check["distance_m"], 2), "off_deg": round(check["off_deg"], 3),
        "is_pano": cam["is_pano"], "focal": cam["focal"],
        "image_width_px": w, "image_height_px": h,
        "bbox_px": None, "detector_confidence": None,
        "outcome": outcome,
        "in_fov": check["in_fov"], "facing_camera": check["facing_camera"],
        "sharp": check["sharp"], "unoccluded": check["unoccluded"],
        "visibility_method": "automatic_rules",
        "model_version": None, "evidence_ref": None,
        "source_metadata": {"provenance": {"snapshot_run_id": snapshot_run_id, "data_revision": data_revision,
                                           "geometry_source": cam["geometry_source"],
                                           "heading_source": cam["heading_source"]}},
    }


def pass2_rows(images, sizes, signs, detected_types, *, snapshot_run_id, negative_enabled, keep, max_dist_m,
               max_facing_diff_deg, checks=None, data_revision=None):
    """`detected_types`: image_id → set mã đã có dòng seen ở lượt 1.

    `checks`: (image_id, sign_id) → {sharp, unoccluded} từ bộ kiểm độc lập; thiếu thì null.
    Chỉ xuất cặp đã nằm trong khung (in_fov true) để người duyệt đọc được, còn lại bỏ qua.
    """
    checks = checks or {}
    rows = []
    for img in images:
        size = sizes.get(str(img["id"]))
        if not size:
            continue
        w, h = size
        for sign in signs:
            if sign.get("status") in SKIP_STATUSES:
                continue
            extra = checks.get((str(img["id"]), sign["sign_id"]), {})
            c = check_pair(img, sign, w=w, h=h, keep=keep, max_dist_m=max_dist_m,
                           max_facing_diff_deg=max_facing_diff_deg, **extra)
            if c is None or c["in_fov"] is not True:
                continue
            outcome = decide_outcome(c, detected_same_type=sign["sign_type_code"] in detected_types.get(str(img["id"]), set()),
                                     negative_enabled=negative_enabled, max_dist_m=max_dist_m)
            rows.append(pass2_row(img, sign, c, outcome, w=w, h=h, snapshot_run_id=snapshot_run_id,
                                  data_revision=data_revision))
    return rows
