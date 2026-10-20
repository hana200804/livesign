import pytest

from livesign_ai.b3 import audit_sample, decide, denominator, detected_pairs, parse_label, summarize

IDS = {"P.102": 0, "P.130": 6}


def lab(image_id, code="P.102", vis="visible", box=("10", "10", "50", "50"), edge="2048", creator="mapillary:1"):
    return parse_label({"image_id": image_id, "sign_type_code": code, "visibility_label": vis, "creator_id": creator,
                        "image_long_edge_px": edge, "is_pano": "false",
                        **dict(zip(("x1", "y1", "x2", "y2"), box))})


def test_denominator_filters_with_reasons():
    labels = [lab("a"), lab("b", vis="unknown"), lab("c", code="P.999"), lab("d", box=("", "", "", "")),
              lab("e", edge="1024")]
    used, skipped = denominator(labels, set(IDS), 2048)
    assert [p["image_id"] for p in used] == ["a"]
    assert sum(skipped.values()) == 4


def test_detected_pairs_needs_type_and_iou():
    pairs = [lab("a"), lab("a", code="P.130", box=("100", "100", "140", "140")), lab("b")]
    preds = {"a": [{"box": [12, 12, 50, 50], "class_id": 0, "score": 0.9},
                   {"box": [100, 100, 140, 140], "class_id": 0, "score": 0.8}]}  # sai loại
    hit = detected_pairs(pairs, preds, IDS, 0.5)
    assert hit == [True, False, False]
    s = summarize(pairs, hit)
    assert s["by"]["all"]["all"] == {"detected": 1, "total": 3, "r": pytest.approx(1 / 3)}
    assert s["unique_images"] == 2


@pytest.mark.parametrize("r,expected", [(None, "not_run"), (0.5, "keep"), (0.49, "secondary_only"),
                                        (0.30, "secondary_only"), (0.29, "drop")])
def test_decide_thresholds(r, expected):
    assert decide(r, 0.5, 0.3) == expected


def test_audit_sample_distinct_and_stable():
    ids = [f"img{i % 80}" for i in range(200)]
    s = audit_sample(ids, 60, seed=0)
    assert len(s) == 60 == len(set(s))
    assert s == audit_sample(ids, 60, seed=0)
    assert audit_sample(["x"], 60, seed=0) == ["x"]
