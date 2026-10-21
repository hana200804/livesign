from livesign_ai.dedup import candidate_pairs, duplicate_groups, exact_duplicate_pairs, hamming
from livesign_ai.manifest import count_objects
from livesign_ai.split import assign_splits, groups_cross_splits


def test_hamming():
    assert hamming(0b1011, 0b0001) == 2
    assert hamming(2**64 - 1, 0) == 64


def test_candidate_pairs_threshold():
    hashes = [("a", 0), ("b", 0b111111), ("c", 0b1111111)]
    assert candidate_pairs(hashes, 6) == [("a", "b", 6), ("b", "c", 1)]


def test_duplicate_groups_are_stable_and_transitive():
    groups = duplicate_groups(["c", "a", "b", "z"], [("c", "b"), ("b", "a")])
    assert groups == {"a": "dup:a", "b": "dup:a", "c": "dup:a", "z": "dup:z"}


def test_exact_duplicate_pairs():
    assert exact_duplicate_pairs({"b": "h1", "a": "h1", "c": "h2"}) == [("a", "b")]


def items(*rows):
    return [{"key": k, "source": s, "orig_split": o, "group_id": g} for k, s, o, g in rows]


def test_keep_rule_moves_conflicting_group_to_train():
    its = items(("a", "s", "train", "g1"), ("b", "s", "test", "g1"), ("c", "s", "val", "g2"))
    split, exc = assign_splits(its, {"s": "keep"})
    assert split == {"a": "train", "b": "train", "c": "val"}
    assert exc == [{"group_id": "g1", "proposed": ["test", "train"], "assigned": "train", "keys": ["a", "b"]}]


def test_whole_source_held_out():
    split, _ = assign_splits(items(("a", "s1", None, "g1"), ("b", "s2", None, "g2")), {"s1": "train", "s2": "test"})
    assert split == {"a": "train", "b": "test"}


def test_by_group_never_splits_a_group():
    its = items(*[(f"k{i}", "s", None, f"g{i // 3}") for i in range(300)])
    split, exc = assign_splits(its, {"s": {"by_group": {"train": 0.8, "val": 0.1, "test": 0.1}}})
    assert exc == []
    assert groups_cross_splits(split, {it["key"]: it["group_id"] for it in its}) == []
    assert set(split.values()) == {"train", "val", "test"}


def test_count_objects():
    out = count_objects([("train", [0, 0, 1]), ("val", [1])], {0: "P.102", 1: "P.130"})
    assert out == {"P.102": {"train": 2, "val": 0, "test": 0}, "P.130": {"train": 1, "val": 1, "test": 0}}
