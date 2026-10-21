from pathlib import Path

import pytest
import yaml

from livesign_ai.dataset import (ImageRec, Obj, count_labels, label_path_for, map_record, parse_names,
                                 parse_yolo_label, read_yolo_source, split_from_path)

NAMES = {0: "no turn left", 1: "no car entry/turning", 2: "speed limit"}
IDS = {"P.123a": 2}


def test_parse_names_list_and_dict():
    assert parse_names(["a", "b"]) == {0: "a", 1: "b"}
    assert parse_names({"0": "a", 3: "b"}) == {0: "a", 3: "b"}


def test_parse_yolo_label():
    objs = parse_yolo_label("0 0.5 0.5 0.1 0.2\n\n2 0.1 0.1 0.05 0.05\n", NAMES)
    assert [o.orig_label for o in objs] == ["no turn left", "speed limit"]
    assert objs[0].yolo == (0.5, 0.5, 0.1, 0.2)


@pytest.mark.parametrize("bad", ["0 0.5 0.5 0.1", "9 0.5 0.5 0.1 0.1"])
def test_parse_yolo_label_rejects_bad_line(bad):
    with pytest.raises(ValueError):
        parse_yolo_label(bad, NAMES)


def test_label_path_both_layouts():
    assert label_path_for(Path("r/images/train/a.png")) == Path("r/labels/train/a.txt")
    assert label_path_for(Path("r/train/images/a.jpg")) == Path("r/train/labels/a.txt")


def test_split_from_path_aliases():
    assert split_from_path(Path("r/valid/images/a.jpg"), Path("r")) == "val"
    assert split_from_path(Path("r/images/a.jpg"), Path("r")) is None


def rec(*labels):
    return ImageRec("s", Path("s/images/train/x.png"), None, "train", [Obj(l, (0.5, 0.5, 0.1, 0.1)) for l in labels])


def test_map_record_keeps_mapped_and_ignores_safe_labels():
    kept, dropped = map_record(rec("no turn left", "speed limit"), {"no turn left": "P.123a"}, {"speed limit"}, IDS)
    assert kept == [(2, (0.5, 0.5, 0.1, 0.1))]
    assert dropped == {"speed limit": 1}


def test_map_record_drops_image_with_ambiguous_label():
    kept, dropped = map_record(rec("no turn left", "no car entry/turning"), {"no turn left": "P.123a"}, set(), IDS)
    assert kept is None
    assert dropped == {"no car entry/turning": 1}


def test_count_labels():
    objects, images = count_labels([rec("a", "a", "b")])
    assert objects[("a", "train")] == 2
    assert images[("a", "train")] == 1


def test_read_yolo_source(tmp_path):
    for split in ("train", "val"):
        (tmp_path / "images" / split).mkdir(parents=True)
        (tmp_path / "labels" / split).mkdir(parents=True)
        (tmp_path / "images" / split / f"{split}1.png").write_bytes(b"")
        (tmp_path / "labels" / split / f"{split}1.txt").write_text("0 0.5 0.5 0.1 0.1\n")
    (tmp_path / "data.yaml").write_text("names: [no turn left]\n")
    recs = read_yolo_source("k", tmp_path, lambda p: yaml.safe_load(Path(p).read_text()))
    assert [(r.image_key, r.orig_split, len(r.objects)) for r in recs] == [("k:train/train1", "train", 1),
                                                                           ("k:val/val1", "val", 1)]


def test_same_file_name_in_two_splits_gets_two_keys(tmp_path):
    for split in ("train", "test"):
        (tmp_path / split / "images").mkdir(parents=True)
        (tmp_path / split / "images" / "11508.png").write_bytes(b"")
    (tmp_path / "data.yaml").write_text("names: [a]\n")
    recs = read_yolo_source("k", tmp_path, lambda p: yaml.safe_load(Path(p).read_text()))
    assert [r.image_key for r in recs] == ["k:test/11508", "k:train/11508"]
    assert recs[0].export_name == "k__test__11508"
