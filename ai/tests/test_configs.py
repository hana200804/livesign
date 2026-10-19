"""dataset.yaml phải là bản chiếu đúng của classes.yaml (nguồn duy nhất của danh mục lớp)."""

from pathlib import Path

import yaml

CONFIGS = Path(__file__).resolve().parents[1] / "configs"


def load(name):
    return yaml.safe_load((CONFIGS / name).read_text(encoding="utf-8"))


def test_dataset_names_match_classes():
    classes = load("classes.yaml")["classes"]
    dataset = load("dataset.yaml")
    assert dataset["names"] == classes
    assert dataset["nc"] == len(classes)


def test_class_ids_are_contiguous_and_unique():
    classes = load("classes.yaml")["classes"]
    assert sorted(classes) == list(range(len(classes)))
    assert len(set(classes.values())) == len(classes)


def test_every_class_has_definition():
    cfg = load("classes.yaml")
    defs = cfg["definitions"]
    for code in cfg["classes"].values():
        assert code in defs, f"thiếu definitions cho {code}"
        assert set(defs[code]["vehicle_types"]) <= set(cfg["vehicle_types"])


def test_source_mappings_point_to_known_classes():
    cfg = load("classes.yaml")
    known = set(cfg["classes"].values())
    for name, src in cfg["sources"].items():
        for target in (src.get("mapping") or {}).values():
            assert target in known, f"{name}: lớp đích {target} không có trong classes"
