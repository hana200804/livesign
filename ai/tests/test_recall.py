from livesign_ai.recall import match_image, recall_by, size_bucket


def test_size_bucket():
    assert size_bucket([0, 0, 31, 31]) == "small"
    assert size_bucket([0, 0, 50, 50]) == "medium"
    assert size_bucket([0, 0, 100, 100]) == "large"


def test_match_requires_same_class_and_one_to_one():
    gts = [{"box": [0, 0, 10, 10], "class_id": 1}, {"box": [0, 0, 10, 10], "class_id": 2}]
    preds = [{"box": [0, 0, 10, 10], "class_id": 1, "score": 0.9},
             {"box": [0, 0, 10, 10], "class_id": 1, "score": 0.8}]
    assert match_image(gts, preds, 0.5) == {0}


def test_recall_by_group_and_empty():
    gts = [{"box": [0, 0, 10, 10], "class_id": 1}, {"box": [50, 50, 60, 60], "class_id": 1}]
    preds = [{"box": [1, 1, 10, 10], "class_id": 1, "score": 0.9}]
    out = recall_by([(gts, preds, {"pano": False})], 0.5, lambda g, m: m["pano"])
    assert out == {False: (1, 2, 0.5)}
    assert recall_by([], 0.5, lambda g, m: 0) == {}
