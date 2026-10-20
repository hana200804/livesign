import pytest

from livesign_ai.boxes import clip_box, iou, nms_per_class, xyxy_to_yolo, yolo_to_xyxy


def test_yolo_round_trip():
    box = yolo_to_xyxy(0.5, 0.25, 0.1, 0.2, 1622, 626)
    assert box == pytest.approx([729.9, 93.9, 892.1, 219.1])
    assert xyxy_to_yolo(box, 1622, 626) == pytest.approx((0.5, 0.25, 0.1, 0.2))


def test_clip_box():
    assert clip_box([-5, 10, 700, 900], 640, 480) == [0, 10, 640, 480]


def test_iou():
    assert iou([0, 0, 10, 10], [0, 0, 10, 10]) == 1
    assert iou([0, 0, 10, 10], [10, 10, 20, 20]) == 0
    assert iou([0, 0, 10, 10], [5, 0, 15, 10]) == pytest.approx(50 / 150)


def test_nms_keeps_other_class_on_same_pole():
    dets = [
        {"box": [0, 0, 10, 10], "score": 0.9, "class_id": 1},
        {"box": [1, 1, 11, 11], "score": 0.8, "class_id": 1},
        {"box": [1, 1, 11, 11], "score": 0.7, "class_id": 2},
    ]
    kept = nms_per_class(dets, 0.45)
    assert [(d["class_id"], d["score"]) for d in kept] == [(1, 0.9), (2, 0.7)]
