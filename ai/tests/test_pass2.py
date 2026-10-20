from livesign_ai.observation import validate_row
from livesign_ai.pass2 import pass2_rows

LON, LAT = 105.85, 21.03
IMG = {"id": 1, "sequence": "s", "creator": {"id": 7}, "captured_at": 1_700_000_000_000,
       "computed_geometry": {"coordinates": [LON, LAT]}, "computed_compass_angle": 0.0,
       "camera_type": "perspective", "camera_parameters": [0.8]}
# Biển 11 m phía bắc, mặt quay về nam (về phía camera).
SIGN = {"sign_id": "sign-1", "sign_type_code": "P.131a", "sign_lon": LON, "sign_lat": LAT + 0.0001,
        "sign_facing_deg": 180.0, "status": "confirmed_auto"}
OK = {"sharp": True, "unoccluded": True}


def run(signs=(SIGN,), detected=None, negative=True, checks=None, img=IMG):
    checks = {("1", "sign-1"): OK} if checks is None else checks
    return pass2_rows([img], {"1": (2048, 1536)}, list(signs), detected or {}, snapshot_run_id="run-1",
                      negative_enabled=negative, keep=0.85, max_dist_m=30, max_facing_diff_deg=45, checks=checks)


def test_not_seen_when_every_condition_holds():
    rows = run()
    assert len(rows) == 1
    r = rows[0]
    assert r["outcome"] == "not_seen" and r["target_sign_id"] == "sign-1"
    assert r["bbox_px"] is None and r["source_metadata"]["provenance"]["snapshot_run_id"] == "run-1"
    assert validate_row(r) == []


def test_unknown_when_negative_evidence_disabled():
    assert run(negative=None)[0]["outcome"] == "unknown"


def test_unknown_when_sharp_or_occlusion_unchecked():
    r = run(checks={})[0]
    assert r["outcome"] == "unknown" and r["sharp"] is None
    assert validate_row(r) == []


def test_unknown_when_old_type_detected():
    assert run(detected={"1": {"P.131a"}})[0]["outcome"] == "unknown"


def test_unknown_when_sign_faces_away():
    assert run(signs=[{**SIGN, "sign_facing_deg": 0.0}])[0]["facing_camera"] is False


def test_unknown_when_focal_is_fallback():
    assert run(img={**IMG, "camera_parameters": []})[0]["outcome"] == "unknown"


def test_skips_far_removed_and_out_of_frame():
    far = {**SIGN, "sign_id": "far", "sign_lat": LAT + 0.001}
    removed = {**SIGN, "sign_id": "rm", "status": "removed"}
    behind = {**SIGN, "sign_id": "back", "sign_lat": LAT - 0.0001}
    assert run(signs=[far, removed, behind]) == []
