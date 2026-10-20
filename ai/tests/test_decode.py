import pytest

from livesign_ai.config import load_classes
from livesign_ai.decode import decode, labels_txt, letterbox_params, model_manifest, to_anchor_rows, unletterbox


def test_letterbox_example_from_contract():
    lb = letterbox_params(1280, 720, 640)
    assert lb == {"r": 0.5, "new_w": 640, "new_h": 360, "left": 0, "top": 140, "size": 640}


def test_letterbox_odd_pad_goes_right_bottom():
    lb = letterbox_params(1622, 626, 640)
    assert (lb["new_w"], lb["new_h"]) == (640, 247)
    assert lb["top"] == 196  # 393 px pad: 196 trên, 197 dưới


def anchors(rows, layout):
    return [list(c) for c in zip(*rows)] if layout == "BCN" else rows


@pytest.mark.parametrize("layout", ["BCN", "BNC"])
def test_decode_example_from_contract(layout):
    # cx=0.5, cy=0.5, w=0.2, h=0.1 normalized; lớp 3 có score cao nhất.
    row = [0.5, 0.5, 0.2, 0.1] + [0.0] * 12
    row[4 + 3] = 0.9
    low = [0.1, 0.1, 0.05, 0.05] + [0.1] * 12
    out = anchors([row, low], layout)
    lb = letterbox_params(1280, 720, 640)
    dets = decode(out, layout=layout, box_units="normalized_xywh", lb=lb, img_w=1280, img_h=720,
                  conf_min=0.25, nms_iou=0.45)
    assert len(dets) == 1
    assert dets[0]["class_id"] == 3 and dets[0]["score"] == 0.9
    assert dets[0]["box"] == pytest.approx([512, 296, 768, 424])


def test_unletterbox_input_box():
    assert unletterbox([256, 288, 384, 352], letterbox_params(1280, 720, 640)) == pytest.approx([512, 296, 768, 424])


def test_decode_dequantizes_int8():
    lb = letterbox_params(640, 640, 640)
    q = [64, 64, 26, 13] + [0] * 12
    q[4] = 115
    dets = decode([q], layout="BNC", box_units="normalized_xywh", lb=lb, img_w=640, img_h=640,
                  conf_min=0.25, nms_iou=0.45, scale=1 / 128, zero_point=0)
    assert dets[0]["class_id"] == 0
    assert dets[0]["score"] == pytest.approx(115 / 128)


def test_decode_rejects_unknown_units():
    with pytest.raises(ValueError):
        decode([[0.5] * 16], layout="BNC", box_units="weird", lb=letterbox_params(10, 10, 640),
               img_w=10, img_h=10, conf_min=0.0, nms_iou=0.45)


def test_to_anchor_rows_rejects_unknown_layout():
    with pytest.raises(ValueError):
        to_anchor_rows([[0]], "NCHW")


def test_labels_txt():
    text = labels_txt(load_classes())
    lines = text.split("\n")
    assert lines[:3] == ["P.102", "P.103a", "P.123a"]
    assert len(lines) == 13 and lines[-1] == ""
    assert not text.startswith("﻿")


def test_model_manifest_reads_quantization():
    m = model_manifest(model_version="v", classes_version="c", params_version="p", config_sha256="h",
                       artifact_sha256="a", exporter_version="e",
                       input_detail={"shape": [1, 640, 640, 3], "dtype": "float32", "quantization": (0.0, 0)},
                       output_detail={"shape": [1, 16, 8400], "dtype": "int8", "quantization": (0.01, -3)},
                       size=640, box_units="normalized_xywh", layout="BCN", conf_min=0.25, nms_iou=0.45)
    assert m["input"]["scale"] is None and m["input"]["zero_point"] is None
    assert (m["output"]["scale"], m["output"]["zero_point"]) == (0.01, -3)
