import pytest

from livesign_ai.config import load_classes
from livesign_ai.geo import bearing_deg, distance_m, ray_offsets
from livesign_ai.observation import (captured_at_utc, camera_from_image, detection_rows, feature_candidates,
                                     in_fov, validate_row)
from livesign_ai.tiling import axis_offsets, merge_tiles, tile_offsets

LON, LAT = 105.85, 21.03
CLASSES = load_classes()["classes"]


def test_tile_offsets_example_from_pipeline():
    assert axis_offsets(2048, 640, 0.2) == [0, 512, 1024, 1408]
    assert axis_offsets(1152, 640, 0.2) == [0, 512]
    assert len(tile_offsets(2048, 1152, 640, 0.2)) == 8


def test_small_image_single_tile():
    assert tile_offsets(500, 400, 640, 0.2) == [(0, 0)]


def test_merge_tiles_dedups_overlap():
    a = {"box": [500, 10, 540, 50], "score": 0.9, "class_id": 1}
    b = {"box": [-12, 10, 28, 50], "score": 0.8, "class_id": 1}  # cùng biển thấy ở ô bên phải
    out = merge_tiles([((0, 0), [a]), ((512, 0), [b])], 2048, 1152, 0.45)
    assert len(out) == 1 and out[0]["tile_origin_px"] == [0, 0]


def test_geo():
    assert distance_m(LON, LAT, LON, LAT + 0.0001) == pytest.approx(11.12, abs=0.01)
    assert bearing_deg(LON, LAT, LON + 0.001, LAT) == pytest.approx(90, abs=0.01)
    along, perp = ray_offsets(LON, LAT, 0, LON + 0.00002, LAT + 0.0001)
    assert along == pytest.approx(11.12, abs=0.01) and perp == pytest.approx(2.08, abs=0.01)


def test_captured_at_utc():
    assert captured_at_utc(1_700_000_000_123) == "2023-11-14T22:13:20.123Z"


IMG = {"id": 123, "sequence": "seqA", "creator": {"id": 77}, "captured_at": 1_700_000_000_000,
       "computed_geometry": {"coordinates": [LON, LAT]}, "geometry": {"coordinates": [0, 0]},
       "computed_compass_angle": 370.0, "camera_type": "perspective", "camera_parameters": [0.8, 0, 0]}


def test_camera_prefers_computed():
    cam = camera_from_image(IMG)
    assert (cam["camera_lon"], cam["camera_heading_deg"], cam["is_pano"], cam["focal"]) == (LON, 10.0, False, 0.8)
    assert cam["geometry_source"] == "computed_geometry"


def test_in_fov():
    assert in_fov(0, 2048, 1536, False, 0.8, 0.85)
    assert not in_fov(40, 2048, 1536, False, 0.8, 0.85)
    assert in_fov(170, 2048, 1024, True, None, 0.85)
    assert in_fov(None, 2048, 1024, False, 0.8, 0.85) is None


def test_feature_candidates_front_same_type_near_ray():
    feats = [
        {"id": 1, "object_value": "regulatory--no-entry--g1", "geometry": {"coordinates": [LON, LAT + 0.0001]}},
        {"id": 2, "object_value": "regulatory--no-entry--g1", "geometry": {"coordinates": [LON, LAT - 0.0001]}},
        {"id": 3, "object_value": "warning--children--g1", "geometry": {"coordinates": [LON, LAT + 0.0001]}},
        {"id": 4, "object_value": "regulatory--no-entry--g1", "geometry": {"coordinates": [LON, LAT + 0.0004]}},
    ]
    cam = {"camera_lon": LON, "camera_lat": LAT}
    out = feature_candidates(cam, 0, feats, {"regulatory--no-entry--g1"}, 30, 5)
    assert [c["source_sign_id"] for c in out] == ["1"]


def rows_for(img, dets):
    return detection_rows(img, dets, w=2048, h=1536, classes=CLASSES,
                          label_candidates={"P.102": ["regulatory--no-entry--g1"]}, features=[],
                          model_version="livesign-yolov8n-qcvn12-r001", keep=0.85, max_dist_m=30, max_ray_m=5,
                          inference_meta={"input_size_px": 640}, data_revision="r001")


def test_detection_rows_valid_and_stable_ids():
    dets = [{"class_id": 0, "score": 0.9, "box": [1500, 100, 1540, 140]},
            {"class_id": 0, "score": 0.7, "box": [1000, 100, 1040, 140]}]
    rows = rows_for(IMG, dets)
    assert [r["observation_id"] for r in rows] == ["mly:123:livesign-yolov8n-qcvn12-r001:0",
                                                    "mly:123:livesign-yolov8n-qcvn12-r001:1"]
    assert rows[0]["bbox_px"][0] == 1000
    for r in rows:
        assert validate_row(r) == []
        assert r["outcome"] == "seen" and r["sign_lon"] is None and r["creator_id"] == "mapillary:77"


def test_missing_camera_gives_unknown():
    img = {**IMG, "computed_geometry": None, "geometry": None}
    row = rows_for(img, [{"class_id": 0, "score": 0.9, "box": [10, 10, 20, 20]}])[0]
    assert row["outcome"] == "unknown" and row["off_deg"] is None and validate_row(row) == []


def test_validate_row_catches_contract_errors():
    good = rows_for(IMG, [{"class_id": 0, "score": 0.9, "box": [10, 10, 20, 20]}])[0]
    assert "bbox_px ra ngoài ảnh" in validate_row({**good, "bbox_px": [10, 10, 3000, 20]})
    assert "camera_lon/camera_lat phải cùng có hoặc cùng thiếu" in validate_row({**good, "camera_lat": None})
    assert any("not_seen" in e for e in validate_row({**good, "outcome": "not_seen"}))
    assert any("NaN" in e or "ngoài" in e for e in validate_row({**good, "detector_confidence": float("nan")}))
