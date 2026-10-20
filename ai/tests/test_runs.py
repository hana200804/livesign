import pytest

from livesign_ai.config import load_classes, load_params
from livesign_ai.runs import check_args, metric_rows, train_args


def test_train_args_from_params_force_no_flip():
    args = train_args(load_params(), where="local", data="d.yaml", name="r001", project="data/runs")
    assert args["fliplr"] == 0.0
    assert (args["imgsz"], args["epochs"], args["batch"]) == (640, 100, 8)
    check_args(args)


def test_check_args_rejects_flip():
    with pytest.raises(ValueError):
        check_args({"fliplr": 0.5, "imgsz": 640, "epochs": 1, "batch": 8})


def test_metric_rows_keep_missing_class_empty():
    classes = load_classes()["classes"]
    summary = [{"Class": "P.102", "Instances": 12, "mAP50": 0.5, "mAP50-95": 0.3, "Box-R": 0.6}]
    rows = metric_rows(summary, model_version="m", classes_version="c", test_manifest_hash="h",
                       inference_mode="full_640", classes=classes)
    assert len(rows) == 12
    assert rows[0]["map50"] == 0.5
    assert rows[1]["object_count"] == 0 and rows[1]["recall"] is None

